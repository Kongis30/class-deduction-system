-- 仅保存旧签字表中有证据、但旧系统没有逐节课次的历史行；不写入消课或财务账本。
CREATE TABLE IF NOT EXISTS private_signature_history (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  package_id BIGINT NOT NULL,
  lesson_date DATE NOT NULL,
  teacher_id BIGINT DEFAULT NULL,
  teacher_name VARCHAR(128) NOT NULL,
  subject_name VARCHAR(128) DEFAULT NULL,
  planned_units INT NOT NULL,
  signature_text VARCHAR(128) DEFAULT NULL,
  source_reference VARCHAR(255) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_private_signature_history (package_id, lesson_date, teacher_name),
  CONSTRAINT fk_private_signature_history_package FOREIGN KEY (package_id) REFERENCES course_package(id),
  CONSTRAINT chk_private_signature_history_units CHECK (planned_units > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
