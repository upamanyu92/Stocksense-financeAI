#!/usr/bin/env python3
"""
Creates a simple but functional TFLite model for stock prediction.
This model takes 30 historical closing prices and predicts direction (DOWN/NEUTRAL/UP).
"""

import numpy as np
import tensorflow as tf
from sklearn.preprocessing import MinMaxScaler
import os

def create_stock_prediction_model():
    """Create a simple LSTM model for stock direction prediction."""

    # Define model architecture
    model = tf.keras.Sequential([
        # Input: [batch_size, 30] - 30 normalized closing prices
        tf.keras.layers.Dense(64, activation='relu', input_shape=(30,)),
        tf.keras.layers.Dropout(0.2),
        tf.keras.layers.Dense(32, activation='relu'),
        tf.keras.layers.Dropout(0.2),
        tf.keras.layers.Dense(16, activation='relu'),
        # Output: [batch_size, 3] - probabilities for [DOWN, NEUTRAL, UP]
        tf.keras.layers.Dense(3, activation='softmax')
    ])

    model.compile(
        optimizer='adam',
        loss='categorical_crossentropy',
        metrics=['accuracy']
    )

    return model

def generate_synthetic_training_data(num_samples=1000):
    """Generate synthetic stock data for training."""
    np.random.seed(42)  # For reproducible results

    X = []
    y = []

    for _ in range(num_samples):
        # Generate a random walk with some trend
        trend = np.random.choice([-1, 0, 1], p=[0.3, 0.4, 0.3])  # DOWN, NEUTRAL, UP
        base_price = 100.0

        # Generate 30 prices with trend
        prices = [base_price]
        for i in range(29):
            # Add trend and random noise
            change = trend * 0.02 + np.random.normal(0, 0.01)
            prices.append(prices[-1] * (1 + change))

        # Normalize the sequence
        prices = np.array(prices)
        normalized = (prices - prices.min()) / (prices.max() - prices.min() + 1e-8)

        X.append(normalized)

        # Create one-hot encoded label
        label = [0, 0, 0]
        if trend == -1:
            label[0] = 1  # DOWN
        elif trend == 1:
            label[2] = 1  # UP
        else:
            label[1] = 1  # NEUTRAL

        y.append(label)

    return np.array(X, dtype=np.float32), np.array(y, dtype=np.float32)

def main():
    print("Creating TFLite stock prediction model...")

    # Create model
    model = create_stock_prediction_model()
    print(f"Model architecture:\n{model.summary()}")

    # Generate synthetic training data
    print("Generating synthetic training data...")
    X_train, y_train = generate_synthetic_training_data(1000)
    X_val, y_val = generate_synthetic_training_data(200)

    print(f"Training data shape: {X_train.shape}")
    print(f"Training labels shape: {y_train.shape}")

    # Train the model briefly
    print("Training model...")
    model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=10,
        batch_size=32,
        verbose=1
    )

    # Convert to TFLite
    print("Converting to TFLite...")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]

    # Set representative dataset for quantization
    def representative_data_gen():
        for i in range(100):
            yield [X_train[i:i+1]]

    converter.representative_dataset = representative_data_gen
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
    converter.inference_input_type = tf.float32
    converter.inference_output_type = tf.float32

    tflite_model = converter.convert()

    # Save the model
    output_path = "stock_prediction.tflite"
    with open(output_path, "wb") as f:
        f.write(tflite_model)

    print(f"TFLite model saved to: {output_path}")
    print(f"Model size: {len(tflite_model)} bytes ({len(tflite_model)/1024:.1f} KB)")

    # Test the TFLite model
    print("\nTesting TFLite model...")
    interpreter = tf.lite.Interpreter(model_content=tflite_model)
    interpreter.allocate_tensors()

    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    print(f"Input details: {input_details}")
    print(f"Output details: {output_details}")

    # Test with a sample
    test_input = X_val[0:1]  # Shape: (1, 30)
    interpreter.set_tensor(input_details[0]['index'], test_input)
    interpreter.invoke()
    output = interpreter.get_tensor(output_details[0]['index'])

    predicted_class = np.argmax(output[0])
    actual_class = np.argmax(y_val[0])
    class_names = ["DOWN", "NEUTRAL", "UP"]

    print(f"Test prediction: {output[0]}")
    print(f"Predicted: {class_names[predicted_class]} (confidence: {output[0][predicted_class]:.3f})")
    print(f"Actual: {class_names[actual_class]}")

    print("\n✅ TFLite model created successfully!")
    return output_path

if __name__ == "__main__":
    main()
