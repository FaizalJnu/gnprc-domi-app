import cv2
import numpy as np
import os

def auto_create_gauge_mask(image_path):
    # Read image
    img = cv2.imread(image_path)
    
    # Convert to grayscale
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    
    # Apply Gaussian blur to reduce noise
    blurred = cv2.GaussianBlur(gray, (5, 5), 0)
    
    # Use adaptive thresholding to handle varying lighting
    thresh = cv2.adaptiveThreshold(
        blurred,
        255,
        cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
        cv2.THRESH_BINARY_INV,
        11,
        2
    )
    
    # Find contours
    contours, _ = cv2.findContours(thresh, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
    
    # Create empty mask
    mask = np.zeros_like(gray)
    
    if contours:
        # Filter contours based on their properties
        potential_needles = []
        for contour in contours:
            # Calculate aspect ratio and area
            rect = cv2.minAreaRect(contour)
            width = rect[1][0]
            height = rect[1][1]
            area = cv2.contourArea(contour)
            
            # Avoid division by zero
            if width == 0 or height == 0:
                continue
                
            aspect_ratio = max(width, height) / min(width, height)
            
            # Filter based on aspect ratio and area
            if aspect_ratio > 3 and area > 100 and area < 5000:
                potential_needles.append(contour)
    
        if potential_needles:
            # Find the most needle-like contour
            needle_contour = max(potential_needles, 
                               key=lambda c: cv2.arcLength(c, False))
            
            # Draw the needle
            cv2.drawContours(mask, [needle_contour], -1, (255), 2)
    
    return mask, thresh  # Return both for debugging

def batch_process(input_dir, output_dir, show_results=True):
    """Process all images in a directory"""
    if not os.path.exists(output_dir):
        os.makedirs(output_dir)
        
    for filename in os.listdir(input_dir):
        if filename.lower().endswith(('.png', '.jpg', '.jpeg')):
            print(f"Processing {filename}")
            image_path = os.path.join(input_dir, filename)
            
            # Generate mask
            mask, thresh = auto_create_gauge_mask(image_path)
            
            if show_results:
                # Show original, threshold, and mask side by side
                original = cv2.imread(image_path)
                thresh_colored = cv2.cvtColor(thresh, cv2.COLOR_GRAY2BGR)
                mask_colored = cv2.cvtColor(mask, cv2.COLOR_GRAY2BGR)
                
                # Resize images to be smaller for display
                scale = 0.5
                width = int(original.shape[1] * scale)
                height = int(original.shape[0] * scale)
                dim = (width, height)
                
                original = cv2.resize(original, dim)
                thresh_colored = cv2.resize(thresh_colored, dim)
                mask_colored = cv2.resize(mask_colored, dim)
                
                # Stack images horizontally
                display = np.hstack([original, thresh_colored, mask_colored])
                
                # Add labels
                cv2.putText(display, 'Original', (10, 30), 
                           cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 0), 2)
                cv2.putText(display, 'Threshold', (width + 10, 30), 
                           cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 0), 2)
                cv2.putText(display, 'Mask', (2*width + 10, 30), 
                           cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 0), 2)
                
                cv2.imshow('Processing Steps', display)
                
                key = cv2.waitKey(0)
                if key == ord('q'):
                    break
                elif key == ord('s'):
                    # Save mask
                    mask_filename = os.path.splitext(filename)[0] + '_mask.png'
                    mask_path = os.path.join(output_dir, mask_filename)c
                    cv2.imwrite(mask_path, mask)
                    print(f"Saved mask: {mask_filename}")
                
                cv2.destroyAllWindows()

if __name__ == "__main__":
    input_directory = "../datasets/archive/sample_synth_datasets/ds5.0/data"
    output_directory = "../datasets/archive/sample_synth_datasets/ds5.0/gtfs1"
    
    batch_process(input_directory, output_directory)