-- ir_demo_development is created by the Docker container (POSTGRES_DB).
-- This script provisions the template database used by integration tests.
drop database if exists ir_demo_test;
create database ir_demo_test;
