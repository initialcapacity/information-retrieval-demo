-- dubjug_development is created by the Docker container (POSTGRES_DB).
-- This script provisions the template database used by integration tests.
drop database if exists dubjug_test;
create database dubjug_test;
