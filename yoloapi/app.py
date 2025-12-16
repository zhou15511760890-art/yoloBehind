from flask import Flask, request, jsonify, send_file
import os
import logging
import requests
from flask_cors import CORS
import predict

# ===================== 全局配置 =====================
app = Flask(__name__)
CORS(app)  # 允许所有跨域请求
app.config['JSON_AS_ASCII'] = False  # 支持中文返回
app.config['MAX_CONTENT_LENGTH'] = 5 * 1024 * 1024  # 限制图片≤5MB

# LLM服务的本地地址
LLM_SERVICE_URL = "http://localhost:8001/generate_disease_advice"

# YOLO模型配置
MODEL_PATH = "./runs/detect/train/weights/best.pt"
CONF_THRESHOLD = 0.5

# 保存路径配置
UPLOAD_PATH = './uploads'
RESULT_PATH = './runs/result.jpg'

# 创建必要的目录
os.makedirs(UPLOAD_PATH, exist_ok=True)
os.makedirs(os.path.dirname(RESULT_PATH), exist_ok=True)

# 日志配置
logging.basicConfig(
    filename='crop_detect.log',
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)

# ===================== 工具函数 =====================
def get_crop_info(disease_name):
    """获取农作物信息（根据病种名称返回对应的农作物信息）"""
    # 从病种名称中提取农作物名称和病害类型
    parts = disease_name.split('___')
    crop_name = parts[0] if parts else "未知作物"
    disease_type = parts[1] if len(parts) > 1 else ""
    
    # 创建基础作物信息结构
    crop_info = {
        "name": disease_name,
        "pests": []
    }
    
    # 如果是健康作物，返回空的病虫害列表
    if "healthy" in disease_name.lower() or disease_type.lower() == "healthy":
        return crop_info
    
    # 根据作物类型设置病虫害信息
    crop_pests = {
        "Apple": ["蚜虫", "红蜘蛛", "锈病", "黑星病"],
        "Corn": ["玉米螟", "蚜虫", "大斑病", "小斑病"],
        "Tomato": ["蚜虫", "红蜘蛛", "早疫病", "晚疫病"],
        "Blueberry": ["蚜虫", "蓝莓蛆", "灰霉病"],
        "Strawberry": ["蚜虫", "红蜘蛛", "叶斑病", "灰霉病"],
        "Grape": ["蚜虫", "红蜘蛛", "黑腐病", "灰霉病"],
        "Orange": ["蚜虫", "红蜘蛛", "黄龙病", "溃疡病"],
        "Peach": ["蚜虫", "红蜘蛛", "细菌性斑点病", "褐腐病"],
        "Potato": ["蚜虫", "晚疫病", "早疫病", "疮痂病"],
        "Cherry": ["蚜虫", "红蜘蛛", "白粉病", "褐腐病"],
        "Soybean": ["蚜虫", "红蜘蛛", "灰斑病", "紫斑病"],
        "Squash": ["蚜虫", "红蜘蛛", "白粉病", "霜霉病"],
        "Raspberry": ["蚜虫", "红蜘蛛", "灰霉病", "炭疽病"],
        "Pepper,_bell": ["蚜虫", "红蜘蛛", "细菌性斑点病", "疫病"]
    }
    
    # 设置病虫害信息
    crop_info["pests"] = crop_pests.get(crop_name, ["未知病虫害"])
    
    return crop_info

# ===================== 核心接口（与原项目main.py保持一致）=====================
@app.route('/api/identify', methods=['POST'])
def api_identify():
    """
    Vue前端对接接口（与原项目main.py中的路由保持一致）：
    - 接收FormData格式图片（key为'image'）
    - 返回识别结果、置信度和农作物信息
    """
    try:
        # 1. 接收图片
        if 'image' not in request.files:
            return jsonify({
                "success": False, 
                "error": "没有上传图片文件"
            }), 400
        
        file = request.files['image']
        if file.filename == '':
            return jsonify({
                "success": False, 
                "error": "没有选择图片"
            }), 400

        # 2. 保存上传的文件
        os.makedirs(UPLOAD_PATH, exist_ok=True)
        # 只使用文件名而不是完整路径来保存文件
        file_name = os.path.basename(file.filename)
        file_path = os.path.join(UPLOAD_PATH, file_name)
        file.save(file_path)
        app.logger.info(f"保存上传图片：{file_path}")

        # 3. 使用原项目的predict.py进行预测
        predictor = predict.ImagePredictor(
            weights_path=MODEL_PATH, 
            img_path=file_path, 
            save_path=RESULT_PATH, 
            kind='crop',  
            conf=CONF_THRESHOLD  
        )
        
        # 执行预测
        results = predictor.predict()
        app.logger.info(f"预测结果：{results}")

        # 4. 删除临时文件
        os.remove(file_path)

        # 5. 解析结果并返回
        if results['labels'] and results['labels'] != '预测失败':
            # 获取农作物信息
            crop_info = get_crop_info(results['labels'][0])
            
            # 调用本地LLM服务生成建议
            try:
                app.logger.info(f"调用LLM服务，病虫害名称: {results['labels'][0]}")
                llm_response = requests.post(
                    LLM_SERVICE_URL,
                    json={
                        "disease_name": results['labels'][0],
                        "confidence": results['confidences'][0] if results['confidences'] else 0.9,
                        "user_question": request.args.get("user_question", "")  # 可选：用户前端传的问题
                    }
                )
                app.logger.info(f"LLM服务响应状态码: {llm_response.status_code}")
                app.logger.info(f"LLM服务响应内容: {llm_response.text}")
                llm_response_json = llm_response.json()
            except Exception as e:
                app.logger.error(f"调用LLM服务失败: {str(e)}")
                llm_response_json = {"prevention_advice": "调用LLM服务失败，请稍后重试"}
            
            # 返回识别结果、结果图片URL和LLM建议
            result_image_url = '/api/result_image'
            
            # 确保advice和disease_intro字段存在
            advice_content = llm_response_json.get("prevention_advice", "未获取到防治建议")
            disease_intro = llm_response_json.get("disease_intro", "未获取到病害介绍")
            app.logger.info(f"返回的病害介绍: {disease_intro}")
            app.logger.info(f"返回的防治建议: {advice_content}")
            
            # 返回识别结果、病虫害类型、病害介绍和LLM建议，不返回预定义的种植建议
            response_data = {
                'success': True,
                'identified': True,
                'crop': {
                    'name': results['labels'][0],
                    'pests': crop_info['pests'],  # 从crop_info获取病虫害类型
                    'tips': []     # 空数组，不显示预定义种植建议
                },
                'confidence': results['confidences'][0] if results['confidences'] else 0.9,
                'result_image_url': result_image_url,
                'disease_intro': disease_intro,
                'advice': advice_content
            }
            
            app.logger.info(f"最终API响应: {response_data}")
            return jsonify(response_data), 200
        else:
            return jsonify({
                'success': False,
                'identified': False,
                'error': '识别失败，请尝试上传更清晰的图片'
            }), 200
            
    except Exception as e:
        app.logger.error(f"识别过程中发生错误: {str(e)}")
        return jsonify({
            'success': False,
            'identified': False,
            'error': f'服务器内部错误: {str(e)}'
        }), 500



# ===================== 结果图片服务接口 =====================
@app.route('/api/result_image', methods=['GET'])
def api_result_image():
    """
    提供带有检测框的结果图片访问
    """
    try:
        return send_file(RESULT_PATH, mimetype='image/jpeg')
    except Exception as e:
        app.logger.error(f"返回结果图片失败：{str(e)}")
        return jsonify({'success': False, 'error': '结果图片获取失败'}), 500

# ===================== 运行配置 =====================
if __name__ == '__main__':
    # 开发环境：直接运行（线上用Gunicorn部署）
    app.run(
        host='0.0.0.0',  # 允许局域网/公网访问
        port=5000,  # 端口（Vue请求时需对应）
        debug=False,  # 线上关闭debug
        threaded=True  # 启用多线程
    )