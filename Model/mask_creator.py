import cv2
import numpy as np
import os
from datetime import datetime

class GaugeMaskCreator:
    def __init__(self):
        self.drawing = False
        self.pts = []
        self.img = None
        self.original = None
        self.mask = None
        self.window_name = 'Gauge Mask Creator'
        # Circle parameters
        self.center_x = 0
        self.center_y = 0
        self.radius = 0
        self.circle_adjustment_step = 1  # pixels to move/adjust per keypress
        self.mode = 'circle'  # 'circle' or 'needle'
        self.temp_img = None  # For storing temporary drawing state

    def create_circular_mask(self, image):
        """Create a mask for the circular gauge face"""
        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        circles = cv2.HoughCircles(
            gray,
            cv2.HOUGH_GRADIENT,
            dp=1,
            minDist=100,
            param1=50,
            param2=30,
            minRadius=100,
            maxRadius=300
        )
        
        if circles is not None:
            circles = np.uint16(np.around(circles))
            circle = circles[0][0]  # Get the first circle
            self.center_x, self.center_y, self.radius = circle[0], circle[1], circle[2]
            return True
        return False

    def mouse_callback(self, event, x, y, flags, param):
        if self.mode == 'needle':
            if event == cv2.EVENT_LBUTTONDOWN:
                self.drawing = True
                self.pts = [(x, y)]
                self.temp_img = self.img.copy()
            
            elif event == cv2.EVENT_MOUSEMOVE and self.drawing:
                self.temp_img = self.img.copy()  # Reset to clean image
                cv2.line(self.temp_img, self.pts[0], (x, y), (0, 255, 0), 2)
            
            elif event == cv2.EVENT_LBUTTONUP:
                self.drawing = False
                if len(self.pts) > 0:
                    # Draw final line
                    cv2.line(self.img, self.pts[0], (x, y), (0, 255, 0), 2)
                    # Create the mask
                    self.mask = np.zeros(self.img.shape[:2], dtype=np.uint8)
                    cv2.line(self.mask, self.pts[0], (x, y), 255, 2)
                self.temp_img = None

    def draw_current_state(self):
        """Redraw the current state of the image with circle and instructions"""
        if self.drawing and self.temp_img is not None:
            displayed_img = self.temp_img
        else:
            displayed_img = self.img.copy()
            cv2.circle(displayed_img, (self.center_x, self.center_y), self.radius, (0, 255, 0), 2)
        
        # Add instructions
        instructions = [
            f"Mode: {self.mode.upper()}",
            "Circle Mode Controls:",
            "Arrow keys: Move circle",
            "+/-: Adjust radius",
            "N: Switch to needle mode",
            "C: Switch to circle mode",
            "R: Reset",
            "S: Save",
            "Q: Quit"
        ]
        
        y = 30
        for instruction in instructions:
            cv2.putText(displayed_img, instruction, (10, y), 
                       cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 0), 2)
            y += 25
            
        return displayed_img

    def process_image(self, image_path, save_dir):
        # Read image
        self.original = cv2.imread(image_path)
        self.img = self.original.copy()
        
        # Find the gauge circle
        self.create_circular_mask(self.img)
        
        # Create window and set mouse callback
        cv2.namedWindow(self.window_name)
        cv2.setMouseCallback(self.window_name, self.mouse_callback)
        
        while True:
            displayed_img = self.draw_current_state()
            cv2.imshow(self.window_name, displayed_img)
            key = cv2.waitKey(1) & 0xFF
            
            # Circle adjustment controls
            if self.mode == 'circle':
                if key == 82:  # Up arrow
                    self.center_y -= self.circle_adjustment_step
                elif key == 84:  # Down arrow
                    self.center_y += self.circle_adjustment_step
                elif key == 81:  # Left arrow
                    self.center_x -= self.circle_adjustment_step
                elif key == 83:  # Right arrow
                    self.center_x += self.circle_adjustment_step
                elif key == ord('+') or key == ord('='):
                    self.radius += self.circle_adjustment_step
                elif key == ord('-') or key == ord('_'):
                    self.radius = max(1, self.radius - self.circle_adjustment_step)
            
            # Mode switching and other controls
            if key == ord('n'):  # Switch to needle mode
                self.mode = 'needle'
            elif key == ord('c'):  # Switch to circle mode
                self.mode = 'circle'
            elif key == ord('s'):  # Save mask
                if self.mask is not None:
                    base_name = os.path.splitext(os.path.basename(image_path))[0]
                    mask_path = os.path.join(save_dir, f"{base_name}_mask.png")
                    cv2.imwrite(mask_path, self.mask)
                    print(f"Mask saved to: {mask_path}")
                    break
            elif key == ord('r'):  # Reset
                self.img = self.original.copy()
                self.mask = None
                self.pts = []
                self.create_circular_mask(self.img)
            elif key == ord('q'):  # Quit
                break
        
        cv2.destroyAllWindows()

def batch_process_images(input_dir, output_dir):
    """Process all images in a directory"""
    if not os.path.exists(output_dir):
        os.makedirs(output_dir)
    
    creator = GaugeMaskCreator()
    
    for filename in os.listdir(input_dir):
        if filename.lower().endswith(('.png', '.jpg', '.jpeg')):
            image_path = os.path.join(input_dir, filename)
            print(f"\nProcessing: {filename}")
            creator.process_image(image_path, output_dir)

# Example usage
if __name__ == "__main__":
    input_directory = "../datasets/archive/sample_synth_datasets/ds5.0/data"
    output_directory = "../datasets/archive/sample_synth_datasets/ds5.0/gtfs"
    
    batch_process_images(input_directory, output_directory)