import os
import torch
import json
from pathlib import Path
from diffusers import StableDiffusionPipeline, EulerDiscreteScheduler
from diffusers.pipelines.stable_diffusion import StableDiffusionPipelineOutput
import logging
import shutil
import onnx
from torch.onnx import export
import numpy as np
import torch.nn as nn

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def create_directory(path: str) -> Path:
    """Create directory if it doesn't exist."""
    path = Path(path)
    path.mkdir(parents=True, exist_ok=True)
    return path

def get_device() -> str:
    """Get the best available device for model loading."""
    if torch.backends.mps.is_available():
        return "mps"
    elif torch.cuda.is_available():
        return "cuda"
    return "cpu"

def setup_pipeline(model_path: str, device: str = "cpu") -> StableDiffusionPipeline:
    """Initialize the SD 3.5 Medium pipeline with optimizations."""
    logger.info(f"Loading pipeline from {model_path}")
    
    # Create optimized scheduler
    scheduler = EulerDiscreteScheduler(
        num_train_timesteps=1000,
        beta_start=0.00085,
        beta_end=0.012,
        beta_schedule="scaled_linear",
        steps_offset=1,
        prediction_type="epsilon",
        timestep_spacing="leading"
    )
    
    # Load the pipeline from the downloaded model
    pipeline = StableDiffusionPipeline.from_pretrained(
        model_path,
        torch_dtype=torch.float32,
        use_safetensors=True,
        safety_checker=None,
        requires_safety_checker=False,
        scheduler=scheduler,
        low_cpu_mem_usage=True
    )
    
    # Memory optimizations
    pipeline.enable_attention_slicing(slice_size="max")
    pipeline.enable_vae_tiling()
    
    return pipeline

def export_text_encoder_to_onnx(text_encoder, output_path: str):
    """Export text encoder to ONNX with proper input handling."""
    logger.info("Starting Text Encoder export...")
    logger.info("Creating dummy inputs for Text Encoder")
    
    with torch.no_grad():
        text_encoder.eval()
        
        # Create dummy inputs that match CLIP's requirements
        input_ids = torch.ones((1, 77), dtype=torch.int64)
        attention_mask = torch.ones((1, 77), dtype=torch.int64)
        
        logger.info("Input shapes - input_ids: %s, attention_mask: %s", input_ids.shape, attention_mask.shape)
        
        # Export with proper input names and dynamic axes
        logger.info("Exporting Text Encoder to ONNX...")
        torch.onnx.export(
            text_encoder,
            (input_ids, attention_mask),
            output_path,
            input_names=["input_ids", "attention_mask"],
            output_names=["last_hidden_state", "pooler_output"],
            dynamic_axes={
                "input_ids": {0: "batch", 1: "sequence"},
                "attention_mask": {0: "batch", 1: "sequence"},
                "last_hidden_state": {0: "batch", 1: "sequence"},
                "pooler_output": {0: "batch"}
            },
            opset_version=17,
            do_constant_folding=True
        )
        logger.info("Text Encoder export completed successfully")

def export_unet_to_onnx(unet, output_path: str):
    """Export UNet to ONNX with proper input handling."""
    logger.info("Starting UNet export...")
    
    class UNetWrapper(torch.nn.Module):
        def __init__(self, unet):
            super().__init__()
            self.unet = unet
        
        def forward(self, latent_model_input, timesteps, encoder_hidden_states):
            logger.info("UNet forward pass - Input shapes:")
            logger.info("- latent_model_input: %s", latent_model_input.shape)
            logger.info("- timesteps: %s", timesteps.shape)
            logger.info("- encoder_hidden_states: %s", encoder_hidden_states.shape)
            
            # Handle the output properly for ONNX export
            added_cond_kwargs = {
                "text_embeds": torch.randn(1, 1280, device=latent_model_input.device),
                "time_ids": torch.randn(1, 6, device=latent_model_input.device)
            }
            
            output = self.unet(
                latent_model_input,
                timesteps,
                encoder_hidden_states=encoder_hidden_states,
                added_cond_kwargs=added_cond_kwargs
            )
            return output.sample
    
    wrapped_unet = UNetWrapper(unet)
    wrapped_unet.eval()
    
    with torch.no_grad():
        logger.info("Creating dummy inputs for UNet")
        # Create dummy inputs that match SD 3.5's architecture
        sample = torch.randn(1, 4, 64, 64)
        timesteps = torch.tensor([999], dtype=torch.int64)
        encoder_hidden_states = torch.randn(1, 77, 2048)  # SD 3.5 uses 2048 dim
        
        logger.info("Exporting UNet to ONNX...")
        torch.onnx.export(
            wrapped_unet,
            (sample, timesteps, encoder_hidden_states),
            output_path,
            input_names=["sample", "timesteps", "encoder_hidden_states"],
            output_names=["output"],
            dynamic_axes={
                "sample": {0: "batch", 2: "height", 3: "width"},
                "encoder_hidden_states": {0: "batch", 1: "sequence"}
            },
            opset_version=17,
            do_constant_folding=True
        )
        logger.info("UNet export completed successfully")

def export_vae_to_onnx(vae, output_path: str):
    """Export VAE decoder to ONNX with proper input handling."""
    logger.info("Starting VAE Decoder export...")
    
    class VAEDecoderWrapper(nn.Module):
        def __init__(self, decoder):
            super().__init__()
            self.decoder = decoder
        
        def forward(self, latents):
            logger.info("VAE forward pass - Input shape: %s", latents.shape)
            # Scale the latents appropriately
            latents = latents / 0.18215
            # Return the decoder output directly
            return self.decoder(latents)
    
    wrapped_decoder = VAEDecoderWrapper(vae.decoder)
    wrapped_decoder.eval()
    
    with torch.no_grad():
        logger.info("Creating dummy input for VAE Decoder")
        # Create dummy input that matches VAE's requirements
        latents = torch.randn(1, 4, 64, 64)
        
        logger.info("Exporting VAE Decoder to ONNX...")
        torch.onnx.export(
            wrapped_decoder,
            (latents,),
            output_path,
            input_names=["latents"],
            output_names=["output"],
            dynamic_axes={
                "latents": {0: "batch", 2: "height", 3: "width"},
                "output": {0: "batch", 2: "height", 3: "width"}
            },
            opset_version=17,
            do_constant_folding=True
        )
        logger.info("VAE Decoder export completed successfully")

def optimize_model(
    pipeline: StableDiffusionPipeline,
    model_path: str,
    output_dir: str,
    optimization_config: dict = None
) -> str:
    """Convert and optimize the model for mobile."""
    output_dir = create_directory(output_dir)
    logger.info(f"Starting model optimization process...")
    logger.info(f"Output directory: {output_dir}")
    
    try:
        # First save the pipeline components
        logger.info("Step 1/4: Saving pipeline components...")
        pipeline.save_pretrained(output_dir)
        
        # Export Text Encoder
        logger.info("Step 2/4: Converting Text Encoder...")
        text_encoder_path = os.path.join(output_dir, "text_encoder.onnx")
        export_text_encoder_to_onnx(pipeline.text_encoder, text_encoder_path)
        
        # Export UNet
        logger.info("Step 3/4: Converting UNet...")
        unet_path = os.path.join(output_dir, "unet.onnx")
        export_unet_to_onnx(pipeline.unet, unet_path)
        
        # Export VAE Decoder
        logger.info("Step 4/4: Converting VAE Decoder...")
        vae_path = os.path.join(output_dir, "vae_decoder.onnx")
        export_vae_to_onnx(pipeline.vae, vae_path)
        
        # Save configurations
        logger.info("Saving model configurations...")
        
        # Save the scheduler configuration
        scheduler_config = pipeline.scheduler.config
        scheduler_path = os.path.join(output_dir, "scheduler_config.json")
        with open(scheduler_path, "w") as f:
            json.dump(scheduler_config, f, indent=2)
        logger.info(f"Saved scheduler config to {scheduler_path}")
        
        # Save model configuration
        model_config = {
            "model_type": "StableDiffusion",
            "version": "3.5-medium",
            "image_size": 768,
            "prediction_type": "epsilon",
            "num_inference_steps": 20,
            "guidance_scale": 7.0,
            "requires_safety_checker": False,
            "text_encoder": {
                "hidden_size": 768,
                "intermediate_size": 3072,
                "max_position_embeddings": 77,
                "num_attention_heads": 12,
                "num_hidden_layers": 12
            },
            "unet": {
                "in_channels": 4,
                "out_channels": 4,
                "down_block_types": [
                    "CrossAttnDownBlock2D",
                    "CrossAttnDownBlock2D",
                    "CrossAttnDownBlock2D",
                    "DownBlock2D"
                ],
                "up_block_types": [
                    "UpBlock2D",
                    "CrossAttnUpBlock2D",
                    "CrossAttnUpBlock2D",
                    "CrossAttnUpBlock2D"
                ],
                "block_out_channels": [320, 640, 1280, 1280],
                "attention_head_dim": 8,
                "cross_attention_dim": 2048  # Updated for SD 3.5
            },
            "vae": {
                "in_channels": 3,
                "out_channels": 3,
                "latent_channels": 4,
                "scaling_factor": 0.18215
            }
        }
        
        config_path = os.path.join(output_dir, "model_config.json")
        with open(config_path, "w") as f:
            json.dump(model_config, f, indent=2)
        logger.info(f"Saved model config to {config_path}")
        
        logger.info("Model optimization completed successfully!")
        logger.info(f"Optimized model saved to: {output_dir}")
        logger.info("Generated files:")
        for file in os.listdir(output_dir):
            logger.info(f"- {file}")
        
        return str(output_dir)
        
    except Exception as e:
        logger.error(f"Error during optimization: {str(e)}", exc_info=True)
        raise

def main():
    try:
        # Setup paths - using the model we just downloaded
        base_dir = Path("/Users/admin/Downloads/VSCode/Android Diffusion App/Model")
        model_path = base_dir / "sd35_medium"  # This is where we downloaded the model
        output_dir = base_dir / "optimized"
        
        if not model_path.exists():
            raise FileNotFoundError(f"Model not found at {model_path}. Please ensure the model was downloaded correctly.")
        
        # Print system info
        device = get_device()
        logger.info(f"PyTorch version: {torch.__version__}")
        logger.info(f"MPS available: {torch.backends.mps.is_available()}")
        logger.info(f"CUDA available: {torch.cuda.is_available()}")
        logger.info(f"Using device: {device}")
        logger.info(f"Model path: {model_path}")
        logger.info(f"Output path: {output_dir}")
        
        # Setup and optimize
        logger.info("Setting up pipeline...")
        pipeline = setup_pipeline(str(model_path), device)
        
        logger.info("Starting optimization process...")
        optimized_dir = optimize_model(
            pipeline,
            str(model_path),
            str(output_dir),
            optimization_config={
                "optimization_level": 99,
                "optimize_for_mobile": True,
                "quantization": "int8",
                "half_precision": True
            }
        )
        
        logger.info(f"Model optimization completed. Optimized model saved to: {optimized_dir}")
        
    except Exception as e:
        logger.error(f"Error in main: {str(e)}")
        raise

if __name__ == "__main__":
    main() 