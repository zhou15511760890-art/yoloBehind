from ultralytics import YOLO
import cv2
import os

# 全局模型，避免重复加载
model = None

class ImagePredictor:
    def __init__(self, weights_path, img_path, save_path, kind=None, conf=0.5):
        self.weights_path = weights_path
        self.img_path = img_path
        self.save_path = save_path
        self.conf = conf
        
        global model
        if model is None:
            model = YOLO(weights_path)
        self.model = model
    
    def predict(self):
        try:
            
            results = self.model.predict(
                source=self.img_path, 
                conf=self.conf, 
                save=False,
                imgsz=640,
                verbose=False
            )
            
            
            labels = []
            confidences = []
            
            for result in results:
                for box in result.boxes:
                    label = result.names[int(box.cls[0])]
                    confidence = float(box.conf[0])
                    labels.append(label)
                    confidences.append(confidence)
            
            
            plotted_image = results[0].plot(boxes=True, labels=True, conf=True)
            bgr_image = cv2.cvtColor(plotted_image, cv2.COLOR_RGB2BGR)
            cv2.imwrite(self.save_path, bgr_image)
            
            return {
                'labels': labels,
                'confidences': confidences
            }
        except Exception as e:
            print(f"预测过程中发生错误: {str(e)}")
            return {
                'labels': [],
                'confidences': []
            }