#!/usr/bin/env python3
import os
import psutil
import time
from functools import wraps
import tracemalloc

def format_bytes(bytes):
    """Format bytes to human readable string"""
    for unit in ['B', 'KB', 'MB', 'GB']:
        if bytes < 1024:
            return f"{bytes:.2f}{unit}"
        bytes /= 1024
    return f"{bytes:.2f}TB"

class MemoryProfiler:
    def __init__(self, log_interval=1.0):
        self.process = psutil.Process(os.getpid())
        self.log_interval = log_interval
        self.peak_memory = 0
        self.start_memory = 0
        
    def start(self):
        """Start memory profiling"""
        tracemalloc.start()
        self.start_memory = self.process.memory_info().rss
        self.peak_memory = self.start_memory
        print(f"Initial memory usage: {format_bytes(self.start_memory)}")
        
    def stop(self):
        """Stop memory profiling and print results"""
        current, peak = tracemalloc.get_traced_memory()
        tracemalloc.stop()
        print("\nMemory Profile Results:")
        print(f"Starting memory: {format_bytes(self.start_memory)}")
        print(f"Peak memory: {format_bytes(self.peak_memory)}")
        print(f"Final memory: {format_bytes(self.process.memory_info().rss)}")
        print(f"Memory increase: {format_bytes(self.process.memory_info().rss - self.start_memory)}")
        
    def log_memory(self, prefix=""):
        """Log current memory usage"""
        current_memory = self.process.memory_info().rss
        if current_memory > self.peak_memory:
            self.peak_memory = current_memory
        print(f"{prefix}Current memory usage: {format_bytes(current_memory)}")

def profile_memory(func):
    """Decorator to profile memory usage of a function"""
    @wraps(func)
    def wrapper(*args, **kwargs):
        profiler = MemoryProfiler()
        profiler.start()
        try:
            result = func(*args, **kwargs)
            return result
        finally:
            profiler.stop()
    return wrapper 