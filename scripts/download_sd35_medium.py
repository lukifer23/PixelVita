import os
from pathlib import Path
from diffusers import StableDiffusionPipeline
import torch
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def main():
    # Check for token
    token = os.getenv("HF_TOKEN")
    if not token:
        logger.error("Please set the HF_TOKEN environment variable with your Hugging Face token")
        return
    
    # Setup paths
    model_dir = Path("/Users/admin/Downloads/VSCode/Android Diffusion App/Model")
    model_dir.mkdir(parents=True, exist_ok=True)
    
    logger.info("Downloading SD 3.5 model...")
    
    # Download the model with token
    pipeline = StableDiffusionPipeline.from_pretrained(
        "stabilityai/stable-diffusion-xl-base-1.0",  # Updated repository name
        torch_dtype=torch.float32,
        use_safetensors=True,
        variant="fp16",
        token=token  # Use the token for authentication
    )
    
    # Save the model
    logger.info("Saving model to disk...")
    pipeline.save_pretrained(
        str(model_dir / "sd35_medium"),
        safe_serialization=True
    )
    
    logger.info("Model downloaded successfully!")

if __name__ == "__main__":
    main() 