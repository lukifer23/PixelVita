import os
import torch
import json
from pathlib import Path
from diffusers import StableDiffusionPipeline, AutoencoderKL
from optimum.onnxruntime import ORTStableDiffusionPipeline
from safetensors.torch import load_file
import shutil
from typing import Optional, Dict, Any
import logging

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
    """Initialize the SD pipeline with optimizations."""
    logger.info(f"Loading pipeline from {model_path}")
    
    # Load the base pipeline
    pipeline = StableDiffusionPipeline.from_pretrained(
        "stabilityai/sd-turbo",  # Changed to SD Turbo base model
        torch_dtype=torch.float32,
        use_safetensors=True,
        variant="fp16",
        safety_checker=None,
        requires_safety_checker=False
    )
    
    # Load custom weights if provided
    if model_path and Path(model_path).exists():
        logger.info("Loading custom weights")
        state_dict = load_file(model_path)
        pipeline.unet.load_state_dict(state_dict)
    
    # Memory optimizations
    pipeline.enable_attention_slicing()
    pipeline.enable_vae_tiling()
    
    # Optimize scheduler for mobile
    pipeline.scheduler.config.update({
        "num_train_timesteps": 1000,
        "beta_start": 0.00085,
        "beta_end": 0.012,
        "beta_schedule": "scaled_linear",
        "steps_offset": 1,
        "clip_sample": False
    })
    
    # Move to device
    pipeline = pipeline.to(device)
    return pipeline

def optimize_model(
    pipeline: StableDiffusionPipeline,
    output_dir: str,
    optimization_config: Optional[Dict[str, Any]] = None
) -> str:
    """Convert and optimize the model for mobile."""
    output_dir = create_directory(output_dir)
    logger.info(f"Converting pipeline to ONNX format in {output_dir}")
    
    # Default optimization configuration for SD Turbo
    default_config = {
        "optimization_level": 99,
        "optimize_for_mobile": True,
        "quantization": "int8",
        "half_precision": True,
        "use_static_shapes": True,
        "use_external_data_format": True,
        "input_names": ["input_ids", "latent_model_input", "timestep"],
        "dynamic_axes": {
            "input_ids": {0: "batch", 1: "sequence"},
            "latent_model_input": {0: "batch", 2: "height", 3: "width"},
            "timestep": {0: "batch"}
        }
    }
    
    config = {**default_config, **(optimization_config or {})}
    
    try:
        # Convert to ONNX with optimizations
        ort_pipeline = ORTStableDiffusionPipeline.from_pretrained(
            pipeline,
            export=True,
            output_dir=output_dir,
            **config
        )
        
        logger.info("Model conversion completed successfully")
        return str(output_dir)
        
    except Exception as e:
        logger.error(f"Error during optimization: {str(e)}")
        raise

def main():
    try:
        # Setup paths
        model_dir = Path("/Users/admin/Downloads/VSCode/Android Diffusion App/Model")
        model_path = model_dir / "sd3.5m_turbo.safetensors"
        output_dir = model_dir / "optimized"
        
        # Print system info
        device = get_device()
        logger.info(f"PyTorch version: {torch.__version__}")
        logger.info(f"MPS available: {torch.backends.mps.is_available()}")
        logger.info(f"CUDA available: {torch.cuda.is_available()}")
        logger.info(f"Using device: {device}")
        
        # Setup and optimize
        pipeline = setup_pipeline(str(model_path), device)
        optimized_dir = optimize_model(
            pipeline,
            str(output_dir),
            optimization_config={
                "optimization_level": 99,
                "optimize_for_mobile": True,
                "quantization": "int8",
                "half_precision": True
            }
        )
        
        logger.info(f"Model optimization completed. Output saved to: {optimized_dir}")
        
    except Exception as e:
        logger.error(f"Error in main: {str(e)}")
        raise

if __name__ == "__main__":
    main() 