use store;
SHOW DATABASES;
ALTER TABLE product_inventory
ADD CONSTRAINT check_quantity_non_negative CHECK (quantity >= 0);

select * from product;
select * from product_file;
select * from product_inventory;
select * from order_table;

SELECT *
FROM order_table
INNER JOIN product ON order_table.product_id = product.id;