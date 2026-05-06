# Robot Control and Scheduling System (RCS)

A lightweight Robot Control & Scheduling System (RCS) designed for robot management, task scheduling, real-time monitoring, and frontend-backend communication practice.

---

# Overview

This project simulates an industrial robot scheduling platform that supports robot registration, command dispatching, task management, and real-time status monitoring.

The system is designed with a frontend-backend separated architecture and focuses on:

* Robot lifecycle management
* Task scheduling and command dispatching
* Real-time robot status updates
* Frontend-backend API interaction
* Polling-based communication
* Industrial scheduling workflow simulation

This project was developed mainly for software engineering learning, distributed task scheduling exploration, and practical full-stack development training.

---

# Features

## Robot Management

* Robot registration
* Robot status monitoring
* Battery level display
* Current task tracking
* Online/offline heartbeat detection

## Task Scheduling

* Task assignment
* Priority-based scheduling
* Command queue simulation
* Task polling mechanism
* Emergency task handling

## Real-Time Monitoring

* Real-time robot state updates
* Dynamic log polling
* Command execution feedback
* Robot detail dashboard

## Frontend Visualization

* Interactive robot monitoring page
* Task status display
* Real-time data refresh
* Responsive dashboard layout

## Backend Services

* RESTful API design
* JSON data interaction
* Scheduling simulation
* Command management
* Status synchronization

---

# Tech Stack

## Frontend

* HTML5
* CSS3
* JavaScript (ES6)
* Fetch API

## Backend Services

### Robot Management Service

* Spring Boot
* REST API
* Java

### Task Scheduling Service

* Spring Boot
* Task Dispatching
* Scheduling Logic
* Command Polling

## Database

* MySQL

## Development Tools

* Git
* GitHub
* IntelliJ IDEA
* VS Code

---

# System Architecture

```text
Frontend Dashboard
        ↓
REST API Communication
        ↓
Robot Management Service
        ↓
Task Scheduling Service
        ↓
Command / Task Processing
        ↓
Database Storage
```

---

# Project Structure

```text
robot-rcs-system/
│
├── frontend/
│   ├── html/
│   ├── css/
│   ├── js/
│
├── robot-management-service/
│   ├── src/
│   ├── pom.xml
│
├── task-scheduling-service/
│   ├── src/
│   ├── pom.xml
│
│
├── screenshots/
│
├── README.md
└── .gitignore
```

---

# Core Functional Modules

## 1. Robot Registration Module

Robots can register themselves to the backend server and maintain heartbeat communication.

Main functions:

* Robot identity registration
* Initial status synchronization
* Heartbeat updates
* Online/offline detection

---

## 2. Scheduling Module

The scheduling module simulates industrial robot task dispatching.

Main functions:

* Task allocation
* Priority scheduling
* Command generation
* Scheduling queue management
* Emergency command processing

---

## 3. Monitoring Module

The monitoring module provides real-time robot information visualization.

Main functions:

* Robot status updates
* Battery monitoring
* Task progress display
* Runtime log polling
* Command execution tracking

---

## 4. Frontend-Backend Communication

The system uses REST APIs for communication between the frontend dashboard and multiple backend services.

Implemented interactions include:

* Robot state synchronization
* Task polling
* Command retrieval
* Log updates
* Scheduling requests

---

# API Design Examples

## Get Robot Status

```http
GET /api/robots
```

## Register Robot

```http
POST /api/robots/register
```

## Dispatch Task

```http
POST /api/tasks/dispatch
```

## Get Scheduling Commands

```http
GET /api/commands/poll
```

---

# Screenshots

## Dashboard

> Add dashboard screenshot here

## Robot Detail Page

> Add robot detail screenshot here

## Scheduling Interface

> Add scheduling interface screenshot here

---

# Future Improvements

Planned future optimizations:

* WebSocket real-time communication
* Path planning algorithms
* Redis message queue integration
* Multi-robot collaborative scheduling
* AI-based scheduling optimization
* Docker deployment
* Authentication and permission system
* Microservice architecture

---

# Learning Outcomes

Through this project, the following skills were practiced:

* Full-stack development
* Frontend-backend separation
* RESTful API design
* Real-time data synchronization
* Robot scheduling logic
* Software engineering workflow
* Git/GitHub collaboration

---

# Running the Project

## Backend

```bash
cd backend
mvn spring-boot:run
```

## Frontend

Open the frontend page directly in browser or deploy using a local web server.

---

# GitHub Repository

GitHub Repository:

[https://github.com/all5634/robot-rcs-system](https://github.com/all5634/robot-rcs-system)

---

# Author

Developed for learning purposes and software engineering practice.
