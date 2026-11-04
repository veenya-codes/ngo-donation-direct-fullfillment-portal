CREATE DATABASE IF NOT EXISTS ngo_db;
USE ngo_db;

DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS items;

CREATE TABLE items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    price DOUBLE NOT NULL,
    requested_by VARCHAR(255) DEFAULT 'NGO',
    category VARCHAR(100) DEFAULT 'General',
    is_urgent INT DEFAULT 0
);

CREATE TABLE orders (
    id INT AUTO_INCREMENT PRIMARY KEY,
    item_name VARCHAR(255) NOT NULL,
    price DOUBLE NOT NULL,
    status VARCHAR(50) NOT NULL
);

INSERT INTO items (name, price, requested_by, category, is_urgent) VALUES 
('Rice Bag 10kg', 450.00, 'Hope Orphanage', 'Food', 1),
('Baby Formula x3', 1200.00, 'Grace Trust', 'Medical', 0),
('Blankets Pack of 5', 850.00, 'Elderly Care Home', 'Clothing', 1),
('Textbooks Grade 5', 600.00, 'Bright Horizon', 'Education', 0);
