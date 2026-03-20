# Rell Seeder

A tool for generating and seeding test data for Rell applications.

## Overview

The Rell Seeder tool helps developers generate realistic test data for their Rell applications. 
It analyzes Rell entity definitions to understand the schema, generates appropriate test data respecting constraints, 
and can export the data in various formats. The generated data can be used to seed a database for testing or development purposes.

## Components

The seeder consists of several components:

1. **Schema Reader**: Parses Rell files to build a schema representation with relations and constraints.
2. **Configuration**: Allows customization of data generation with rules for specific fields and entities.
3. **Data Generator**: Generates random or fake data respecting the schema and configuration.
4. **Data Output**: Exports generated data in various formats (JSON, YAML, SQL, CSV, Rell).
5. **Seeder**(TODO): Reads data from exported files and inserts it into a database.

### Configuration

You can customize data generation by providing a configuration file in JSON or YAML format. The configuration allows you to:

- Define the number of records to generate for each entity
- Provide predefined values for specific fields
- Set ranges for numeric fields
- Configure string length and patterns
- Set probabilities for nullable fields
- Define custom rules and constraints

Example configuration (YAML):

seeder.yaml
```yml
entities:
   - entities/main.yml 
```

entities/main.yml
```yml
module: main

user:
  count: 100
  attributes:
     email:
       type: "predefined"
       values: ["user1@example.com", "user2@example.com"]
     age:
       type: "range"
       min: 18
       max: 65
     name:
       type: "text"
       min: 5
       max: 50
  
product:
   count: 200
   attributes:
     price:
       type: "range"
       min: 10
       max: 1000
```


## Building

To build the seeder:

```bash
./gradlew :seeder:build
```
