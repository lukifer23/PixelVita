#!/usr/bin/env python3
"""Simple benchmarking harness for scheduler step performance.

This script runs a number of scheduler steps on randomly generated data and
prints the elapsed time. It is intended to provide a rough comparison between
different scheduler implementations.
"""

import argparse
import time
import numpy as np


def run_benchmark(size: int, steps: int) -> float:
    latents = np.random.randn(size).astype(np.float32)
    model_out = np.random.randn(size).astype(np.float32)

    start = time.time()
    for _ in range(steps):
        # simple operation mirroring scheduler math
        latents = latents - model_out * 0.1
    end = time.time()
    return end - start


def main():
    parser = argparse.ArgumentParser(description="Profile scheduler operations")
    parser.add_argument("--size", type=int, default=4 * 64 * 64,
                        help="Number of latent elements")
    parser.add_argument("--steps", type=int, default=20,
                        help="Number of inference steps to simulate")
    args = parser.parse_args()

    duration = run_benchmark(args.size, args.steps)
    print(f"Simulated {args.steps} steps on {args.size} elements in {duration:.4f}s")


if __name__ == "__main__":
    main()

