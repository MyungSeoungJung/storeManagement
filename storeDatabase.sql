use store;
SHOW DATABASES;
ALTER TABLE product_inventory
ADD CONSTRAINT check_quantity_non_negative CHECK (quantity >= 0);
ALTER TABLE product
ADD CONSTRAINT check_maximum_purchase_quantity_non_negative CHECK (maximum_purchase_quantity > 0);

select * from product;
select * from product_file;
select * from product_inventory;
select * from order_table;

SELECT *
FROM order_table
INNER JOIN product ON order_table.product_id = product.id;

INSERT INTO order_table (order_id, user_id, product_id, quantity, address, order_date, order_status)
VALUES
-- 30개의 주문 데이터를 생성, 모든 주문은 2022년에 생성됨
(1, 1, 1, 5, '주소1', '2022-01-15 12:30:00', 1),
(2, 2, 2, 3, '주소2', '2022-02-20 14:45:00', 1),
(3, 3, 3, 2, '주소3', '2022-03-10 08:15:00', 1),
(4, 4, 4, 6, '주소4', '2022-04-05 19:20:00', 1),
(5, 5, 5, 4, '주소5', '2022-05-25 10:30:00', 1),
(6, 6, 1, 7, '주소6', '2022-06-16 11:50:00', 1),
(7, 7, 2, 8, '주소7', '2022-07-29 17:40:00', 1),
(8, 8, 3, 3, '주소8', '2022-08-12 13:10:00', 1),
(9, 9, 4, 4, '주소9', '2022-09-27 16:25:00', 1),
(10, 10, 5, 5, '주소10', '2022-10-03 09:55:00', 1),
(11, 11, 1, 9, '주소11', '2022-11-18 18:05:00', 1),
(12, 12, 2, 7, '주소12', '2022-12-07 15:40:00', 1),
(13, 13, 3, 4, '주소13', '2022-01-15 12:30:00', 1),
(14, 14, 4, 3, '주소14', '2022-02-20 14:45:00', 1),
(15, 15, 5, 6, '주소15', '2022-03-10 08:15:00', 1),
(16, 16, 1, 8, '주소16', '2022-04-05 19:20:00', 1),
(17, 17, 2, 5, '주소17', '2022-05-25 10:30:00', 1),
(18, 18, 3, 7, '주소18', '2022-06-16 11:50:00', 1),
(19, 19, 4, 9, '주소19', '2022-07-29 17:40:00', 1),
(20, 20, 5, 4, '주소20', '2022-08-12 13:10:00', 1),
(21, 1, 1, 5, '주소21', '2022-09-15 12:30:00', 1),
(22, 2, 2, 3, '주소22', '2022-10-20 14:45:00', 1),
(23, 3, 3, 2, '주소23', '2022-11-10 08:15:00', 1),
(24, 4, 4, 6, '주소24', '2022-12-05 19:20:00', 1),
(25, 5, 5, 4, '주소25', '2022-01-25 10:30:00', 1),
(26, 6, 1, 7, '주소26', '2022-02-16 11:50:00', 1),
(27, 7, 2, 8, '주소27', '2022-03-29 17:40:00', 1),
(28, 8, 3, 3, '주소28', '2022-04-12 13:10:00', 1),
(29, 9, 4, 4, '주소29', '2022-05-27 16:25:00', 1),
(30, 10, 5, 5, '주소30', '2022-06-03 09:55:00', 1),
(31, 10, 1, 5, '주소30', '2022-06-03 09:55:00', 1);
