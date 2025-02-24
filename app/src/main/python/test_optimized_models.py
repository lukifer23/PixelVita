#!/usr/bin/env python3
import os
import onnx
import onnxruntime as ort
import numpy as np
from memory_profiler import profile_memory
import time

class ModelTester:
    def __init__(self, model_dir):
        self.model_dir = model_dir
        self.sess_options = ort.SessionOptions()
        self.sess_options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
        self.sess_options.enable_mem_pattern = True
        self.sess_options.enable_mem_reuse = True
        
    def create_dummy_input(self, input_shape):
        """Create dummy input data for testing"""
        return np.random.randn(*input_shape).astype(np.float32)
    
    @profile_memory
    def test_text_encoder(self):
        print("\nTesting Text Encoder...")
        model_path = os.path.join(self.model_dir, "text_encoder.onnx")
        
        # Load and check model
        model = onnx.load(model_path)
        onnx.checker.check_model(model)
        
        # Create inference session
        session = ort.InferenceSession(model_path, self.sess_options)
        
        # Prepare dummy input
        input_ids = np.random.randint(0, 1000, size=(1, 77), dtype=np.int64)
        
        # Run inference
        start_time = time.time()
        outputs = session.run(None, {"input_ids": input_ids})
        inference_time = time.time() - start_time
        
        print(f"Text Encoder inference time: {inference_time:.2f}s")
        return True
    
    @profile_memory
    def test_unet(self):
        print("\nTesting UNet...")
        model_path = os.path.join(self.model_dir, "unet.onnx")
        
        # Load and check model
        model = onnx.load(model_path)
        onnx.checker.check_model(model)
        
        # Create inference session
        session = ort.InferenceSession(model_path, self.sess_options)
        
        # Prepare dummy inputs
        latent_shape = (2, 4, 64, 64)  # Batch size 2 for classifier-free guidance
        timestep_shape = (1,)
        encoder_hidden_states_shape = (2, 77, 768)
        
        latents = self.create_dummy_input(latent_shape)
        timestep = np.array([50], dtype=np.int64)
        encoder_hidden_states = self.create_dummy_input(encoder_hidden_states_shape)
        
        # Run inference
        start_time = time.time()
        outputs = session.run(None, {
            "sample": latents,
            "timestep": timestep,
            "encoder_hidden_states": encoder_hidden_states
        })
        inference_time = time.time() - start_time
        
        print(f"UNet inference time: {inference_time:.2f}s")
        return True
    
    @profile_memory
    def test_vae(self):
        print("\nTesting VAE Decoder...")
        model_path = os.path.join(self.model_dir, "vae_decoder.onnx")
        
        # Load and check model
        model = onnx.load(model_path)
        onnx.checker.check_model(model)
        
        # Create inference session
        session = ort.InferenceSession(model_path, self.sess_options)
        
        # Prepare dummy input
        latent_shape = (1, 4, 64, 64)
        latents = self.create_dummy_input(latent_shape)
        
        # Run inference
        start_time = time.time()
        outputs = session.run(None, {"latent": latents})
        inference_time = time.time() - start_time
        
        print(f"VAE inference time: {inference_time:.2f}s")
        return True

def main():
    # Test original models
    print("Testing original models...")
    original_tester = ModelTester("../assets/models/sd35_medium")
    try:
        original_tester.test_text_encoder()
        original_tester.test_unet()
        original_tester.test_vae()
    except Exception as e:
        print(f"Error testing original models: {str(e)}")
    
    # Test optimized models
    print("\nTesting optimized models...")
    optimized_tester = ModelTester("../assets/models/sd35_medium_optimized")
    try:
        optimized_tester.test_text_encoder()
        optimized_tester.test_unet()
        optimized_tester.test_vae()
    except Exception as e:
        print(f"Error testing optimized models: {str(e)}")

if __name__ == "__main__":
    main() 