use store;
SHOW DATABASES;
ALTER TABLE product_inventory
ADD CONSTRAINT check_quantity_non_negative CHECK (quantity >= 0);


select * from product;
select * from product_file;
select * from product_inventory;
select * from order_table;
select * from order_state;

INSERT INTO order_table (order_id, user_id, product_id, quantity, address, order_date)
VALUES
  (1, 1, 1, 1, 'Address 1', NOW());
INSERT INTO order_state (order_id, orderStatus)
VALUES
  (1, '처리완료');
  