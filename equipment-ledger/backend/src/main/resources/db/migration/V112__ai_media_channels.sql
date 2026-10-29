ALTER TABLE eam_ai_setting
 ADD vision_model VARCHAR(128) NOT NULL DEFAULT '' COMMENT '视觉模型版本',
 ADD audio_model VARCHAR(128) NOT NULL DEFAULT '' COMMENT '语音识别模型版本',
 ADD vision_input_price DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '视觉输入每百万Token估算价格',
 ADD vision_output_price DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '视觉输出每百万Token估算价格',
 ADD audio_minute_price DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '语音每分钟估算价格人民币';
