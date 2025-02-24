#!/usr/bin/env python3
import os
import sys
import onnx
import numpy as np
from onnxruntime.quantization import quantize_dynamic, QuantType
import onnxruntime as ort

def optimize_model(model_path, output_path, model_type):
    """
    Optimize and quantize an ONNX model for mobile deployment.
    
    Args:
        model_path: Path to the input ONNX model
        output_path: Path to save the optimized model
        model_type: Type of model ('text_encoder', 'unet', or 'vae')
    """
    print(f"Optimizing {model_type} model...")
    
    try:
        # Step 1: Load and basic optimization
        print("Loading model...")
        try:
            # Load model with external data
            model = onnx.load_model(model_path)
            print("Model loaded successfully")
            
            # Save model with all data in one file
            print("Converting external data to internal...")
            temp_model_path = output_path + ".tmp"
            onnx.save_model(model, temp_model_path, save_as_external_data=False)
            model = onnx.load_model(temp_model_path)
            if os.path.exists(temp_model_path):
                os.remove(temp_model_path)
        except Exception as e:
            print(f"Warning during model loading: {str(e)}")
            raise
        
        # Step 2: Graph optimization
        print("Applying graph optimizations...")
        sess_options = ort.SessionOptions()
        sess_options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
        sess_options.enable_mem_pattern = True
        sess_options.enable_mem_reuse = True
        sess_options.intra_op_num_threads = 1
        sess_options.inter_op_num_threads = 1
        
        # Create temporary optimized model
        temp_path = output_path + ".temp"
        try:
            onnx.save_model(model, temp_path, save_as_external_data=False)
        except Exception as e:
            print(f"Warning during model saving: {str(e)}")
            print("Trying alternative saving method...")
            onnx.save(model, temp_path)
        
        # Step 3: INT8 Quantization
        print("Applying INT8 quantization...")
        try:
            quantize_dynamic(
                model_input=temp_path,
                model_output=output_path,
                weight_type=QuantType.QInt8,
                per_channel=False,
                reduce_range=False,
                op_types_to_quantize=['Conv', 'MatMul', 'Gemm', 'Attention']
            )
        except Exception as e:
            print(f"Warning during quantization: {str(e)}")
            print("Falling back to basic optimization...")
            try:
                onnx.save_model(model, output_path, save_as_external_data=False)
            except Exception as e2:
                print(f"Warning during model saving: {str(e2)}")
                print("Trying alternative saving method...")
                onnx.save(model, output_path)
        
        # Clean up temporary files
        if os.path.exists(temp_path):
            os.remove(temp_path)
        
        print(f"Model optimized and saved to: {output_path}")
        
        # Calculate and print size reduction
        original_size = os.path.getsize(model_path) / (1024 * 1024)
        optimized_size = os.path.getsize(output_path) / (1024 * 1024)
        reduction = (1 - optimized_size/original_size) * 100
        
        print(f"Original size: {original_size:.2f}MB")
        print(f"Optimized size: {optimized_size:.2f}MB")
        print(f"Size reduction: {reduction:.1f}%")
        
        # Verify the optimized model
        try:
            print("Verifying optimized model...")
            onnx.checker.check_model(output_path)
            sess_options = ort.SessionOptions()
            sess_options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
            _ = ort.InferenceSession(output_path, sess_options)
            print("Model verification successful")
        except Exception as e:
            print(f"Error verifying model: {str(e)}")
            raise
    except Exception as e:
        print(f"Error during optimization: {str(e)}")
        raise

def main():
    if len(sys.argv) != 4:
        print("Usage: optimize_model.py <input_model> <output_model> <model_type>")
        sys.exit(1)
    
    input_model = sys.argv[1]
    output_model = sys.argv[2]
    model_type = sys.argv[3]
    
    if not os.path.exists(input_model):
        print(f"Error: Input model {input_model} does not exist")
        sys.exit(1)
    
    if model_type not in ['text_encoder', 'unet', 'vae']:
        print("Error: model_type must be one of: text_encoder, unet, vae")
        sys.exit(1)
    
    try:
        optimize_model(input_model, output_model, model_type)
    except Exception as e:
        print(f"Error during optimization: {str(e)}")
        sys.exit(1)

if __name__ == "__main__":
    main() 