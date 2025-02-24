#!/bin/bash
set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Setting up Python environment...${NC}"

# Check if Python 3 is installed
if ! command -v python3 &> /dev/null; then
    echo -e "${RED}Python 3 is not installed. Please install Python 3 first.${NC}"
    exit 1
fi

# Create and activate virtual environment
if [ ! -d "venv" ]; then
    echo "Creating virtual environment..."
    python3 -m venv venv
fi

# Activate virtual environment
source venv/bin/activate

# Install requirements
echo "Installing dependencies..."
pip install -r requirements.txt

# Create output directory
echo "Creating output directories..."
mkdir -p ../assets/models/sd35_medium_optimized

# Function to check if input model exists
check_model() {
    if [ ! -f "$1" ]; then
        echo -e "${RED}Error: Model file $1 not found${NC}"
        exit 1
    fi
}

# Check if input models exist
check_model "../assets/models/sd35_medium/text_encoder.onnx"
check_model "../assets/models/sd35_medium/unet.onnx"
check_model "../assets/models/sd35_medium/vae_decoder.onnx"

echo -e "${YELLOW}Starting model optimization...${NC}"

# Optimize text encoder
echo -e "${YELLOW}Optimizing text encoder...${NC}"
python optimize_model.py \
    ../assets/models/sd35_medium/text_encoder.onnx \
    ../assets/models/sd35_medium_optimized/text_encoder.onnx \
    text_encoder

# Optimize UNet
echo -e "${YELLOW}Optimizing UNet...${NC}"
python optimize_model.py \
    ../assets/models/sd35_medium/unet.onnx \
    ../assets/models/sd35_medium_optimized/unet.onnx \
    unet

# Optimize VAE
echo -e "${YELLOW}Optimizing VAE...${NC}"
python optimize_model.py \
    ../assets/models/sd35_medium/vae_decoder.onnx \
    ../assets/models/sd35_medium_optimized/vae_decoder.onnx \
    vae

# Copy and update model config
echo "Updating model configuration..."
cp ../assets/models/sd35_medium/model_config.json \
   ../assets/models/sd35_medium_optimized/

# Verify optimization results
verify_optimization() {
    local model_name=$1
    local model_path="../assets/models/sd35_medium_optimized/$model_name.onnx"
    if [ ! -f "$model_path" ]; then
        echo -e "${RED}Error: Optimized model $model_path was not created${NC}"
        exit 1
    fi
    local size=$(ls -lh "$model_path" | awk '{print $5}')
    echo -e "${GREEN}Successfully optimized $model_name (Size: $size)${NC}"
}

verify_optimization "text_encoder"
verify_optimization "unet"
verify_optimization "vae_decoder"

echo -e "${GREEN}All models optimized successfully!${NC}"
echo -e "${YELLOW}Optimized models are in: ../assets/models/sd35_medium_optimized/${NC}"

# Deactivate virtual environment
deactivate 