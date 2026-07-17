# Tenant Admin – Deployment Guide

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Project Structure](#project-structure)
4. [Local Development with Docker Compose](#local-development-with-docker-compose)
5. [Build & Push Docker Image](#build--push-docker-image)
6. [AWS ECS Fargate Prerequisites](#aws-ecs-fargate-prerequisites)
7. [ECS Task Definition Explained](#ecs-task-definition-explained)
8. [ECS Service Configuration](#ecs-service-configuration)
9. [ECS Fargate Deployment Walkthrough](#ecs-fargate-deployment-walkthrough)
10. [ECS-Specific Troubleshooting](#ecs-specific-troubleshooting)
11. [ECS Fargate Scaling & Management](#ecs-fargate-scaling--management)
12. [Configuration Management](#configuration-management)
13. [Security Considerations](#security-considerations)
14. [Java-Specific Notes](#java-specific-notes)

---

## Overview

**Application**: Tenant Admin (`mini-java-app`)  
**Language / Runtime**: Java 11  
**Build Tool**: Apache Maven 3.9.x  
**Framework**: Spring Boot 2.7.0 + custom embedded HTTP health server  
**Application Port**: `8080`  
**Health-Check Port**: `8081` → `GET /health` returns `{"status":"UP"}`  
**Target Platform**: AWS ECS Fargate  
**Base Image (runtime)**: `eclipse-temurin:11-jdk-alpine`

---

## Prerequisites

### Local Development
| Tool | Minimum Version | Notes |
|------|----------------|-------|
| Docker | 20.10+ | Docker Desktop or Docker Engine |
| Docker Compose | 2.x | Bundled with Docker Desktop |
| Java JDK | 11 | For local builds outside Docker |
| Apache Maven | 3.8+ | For local builds outside Docker |

### AWS Deployment
| Tool | Minimum Version | Notes |
|------|----------------|-------|
| AWS CLI | v2 | `aws --version` |
| IAM permissions | — | See [IAM Roles](#iam-roles) section |
| Docker | 20.10+ | For building and pushing images |

---

## Project Structure

```
Tenant Admin/
├── Dockerfile                  # Multi-stage build (Java 11 / Maven)
├── docker-compose.yml          # Local development (app only)
├── .dockerignore               # Excludes build artefacts & wrapper files
├── pom.xml                     # Maven project descriptor
├── src/
│   └── main/
│       ├── java/com/test/
│       │   ├── MiniApp.java
│       │   ├── HealthController.java
│       │   └── DatabaseService.java
│       └── resources/
│           └── application.properties
├── ecs/
│   ├── task-definition.json    # ECS Fargate task definition
│   └── service-definition.json # ECS Fargate service definition
├── scripts/
│   ├── build-push.sh           # Linux/macOS build & push
│   ├── build-push.bat          # Windows build & push
│   ├── deploy-image.sh         # Linux/macOS ECS deployment
│   └── deploy-image.bat        # Windows ECS deployment
└── docs/
    └── DEPLOYMENT.md           # This file
```

---

## Local Development with Docker Compose

### 1. Configure environment variables

Create a `.env` file in the project root (never commit this file):

```dotenv
# Database
DB_URL=jdbc:mysql://your-db-host:3306/mini_app_db
DB_USERNAME=appuser
DB_PASSWORD=supersecret

# Redis
REDIS_HOST=your-redis-host
REDIS_PORT=6379

# External services
EXTERNAL_API_BASE_URL=http://api.example.com/v1
PAYMENT_SERVICE_URL=https://payment.example.com/process
```

### 2. Build and start the application

```bash
# Build image and start container
docker compose up --build

# Run in background
docker compose up --build -d

# View logs
docker compose logs -f tenant-admin

# Stop
docker compose down
```

### 3. Verify the application

```bash
# Application
curl http://localhost:8080/mini-app/

# Health check
curl http://localhost:8081/health
# Expected: {"status":"UP"}
```

---

## Build & Push Docker Image

### Linux / macOS

```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

### Windows

```cmd
scripts\build-push.bat
```

The script will prompt you to:
1. Choose a registry (AWS ECR or Docker Hub)
2. Provide registry credentials / region
3. Enter an image tag (defaults to `latest`)

The image name is automatically sanitised to lowercase with hyphens: `tenant-admin`.

---

## AWS ECS Fargate Prerequisites

### IAM Roles

#### ecsTaskExecutionRole
Required for ECS to pull images from ECR and write logs to CloudWatch.

```bash
# Create the role (if it doesn't exist)
aws iam create-role \
  --role-name ecsTaskExecutionRole \
  --assume-role-policy-document '{
    "Version":"2012-10-17",
    "Statement":[{
      "Effect":"Allow",
      "Principal":{"Service":"ecs-tasks.amazonaws.com"},
      "Action":"sts:AssumeRole"
    }]
  }'

# Attach the managed policy
aws iam attach-role-policy \
  --role-name ecsTaskExecutionRole \
  --policy-arn arn:aws:managed-policy/service-role/AmazonECSTaskExecutionRolePolicy
```

#### ecsTaskRole (optional)
Required if the application needs to call other AWS services (S3, SSM, etc.).

```bash
aws iam create-role \
  --role-name ecsTaskRole \
  --assume-role-policy-document '{
    "Version":"2012-10-17",
    "Statement":[{
      "Effect":"Allow",
      "Principal":{"Service":"ecs-tasks.amazonaws.com"},
      "Action":"sts:AssumeRole"
    }]
  }'
```

### Networking

- **VPC**: A VPC with at least two subnets in different Availability Zones.
- **Subnets**: Private subnets (recommended) or public subnets with `assignPublicIp: ENABLED`.
- **Security Group**: Must allow:
  - Inbound TCP `8080` from ALB security group (or `0.0.0.0/0` for testing)
  - Inbound TCP `8081` from ALB / health-check source
  - Outbound `443` to ECR, CloudWatch, SSM endpoints

### ECR Repository

```bash
aws ecr create-repository \
  --repository-name tenant-admin \
  --region us-east-1
```

### CloudWatch Log Group

```bash
aws logs create-log-group \
  --log-group-name /ecs/tenant-admin \
  --region us-east-1
```

### SSM Parameter Store (Secrets)

Store sensitive values in SSM Parameter Store (SecureString):

```bash
aws ssm put-parameter --name /tenant-admin/DB_URL      --value "jdbc:mysql://..." --type SecureString --region us-east-1
aws ssm put-parameter --name /tenant-admin/DB_USERNAME  --value "appuser"          --type SecureString --region us-east-1
aws ssm put-parameter --name /tenant-admin/DB_PASSWORD  --value "supersecret"      --type SecureString --region us-east-1
aws ssm put-parameter --name /tenant-admin/REDIS_HOST   --value "redis-host"       --type SecureString --region us-east-1
aws ssm put-parameter --name /tenant-admin/REDIS_PORT   --value "6379"             --type SecureString --region us-east-1
```

The `ecsTaskExecutionRole` must have `ssm:GetParameters` and `kms:Decrypt` permissions to read SecureString parameters.

---

## ECS Task Definition Explained

File: `ecs/task-definition.json`

| Field | Value | Notes |
|-------|-------|-------|
| `family` | `tenant-admin-task` | Task definition family name |
| `requiresCompatibilities` | `["FARGATE"]` | Fargate launch type |
| `networkMode` | `awsvpc` | Required for Fargate |
| `cpu` | `"512"` | 0.5 vCPU |
| `memory` | `"1024"` | 1 GB RAM |
| `executionRoleArn` | `ecsTaskExecutionRole` | ECR pull + CloudWatch logs |
| `taskRoleArn` | `ecsTaskRole` | Application AWS API calls |

### Container Definition Highlights

- **Image**: Replaced at deploy time with the full ECR URI.
- **Ports**: `8080` (app) and `8081` (health).
- **Environment**: Non-sensitive config values set directly.
- **Secrets**: Sensitive values (`DB_PASSWORD`, etc.) fetched from SSM Parameter Store at task start.
- **Logging**: `awslogs` driver → CloudWatch log group `/ecs/tenant-admin`.

### Valid Fargate CPU / Memory Combinations

| CPU | Memory options |
|-----|---------------|
| 256 | 512, 1024, 2048 MB |
| **512** | **1024**, 2048, 3072, 4096 MB |
| 1024 | 2048–8192 MB |
| 2048 | 4096–16384 MB |
| 4096 | 8192–30720 MB |

---

## ECS Service Configuration

File: `ecs/service-definition.json`

| Field | Value | Notes |
|-------|-------|-------|
| `launchType` | `FARGATE` | Serverless compute |
| `desiredCount` | `2` | Two tasks for HA |
| `networkMode` | `awsvpc` | Each task gets its own ENI |
| `assignPublicIp` | `ENABLED` | Required for public subnets |
| `maximumPercent` | `200` | Rolling deploy: up to 4 tasks |
| `minimumHealthyPercent` | `50` | At least 1 task always running |

---

## ECS Fargate Deployment Walkthrough

### Step 1 – Push the Docker image

```bash
./scripts/build-push.sh
# Select AWS ECR, enter region and account details
```

### Step 2 – Run the deployment script

```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

The script will:
1. Prompt for region, cluster name, image URI, VPC, subnets, security group.
2. Resolve your AWS Account ID automatically.
3. Create the CloudWatch log group if it doesn't exist.
4. Create the ECS cluster if it doesn't exist.
5. Optionally create an Application Load Balancer and Target Group.
6. Register the task definition with all placeholders replaced.
7. Create or update the ECS service.
8. Wait for the service to stabilise.
9. Print the deployment summary.

### Step 3 – Verify

```bash
# List running tasks
aws ecs list-tasks --cluster tenant-admin-cluster --service-name tenant-admin-service

# Describe service
aws ecs describe-services --cluster tenant-admin-cluster --services tenant-admin-service

# Tail logs
aws logs tail /ecs/tenant-admin --follow --region us-east-1
```

---

## ECS-Specific Troubleshooting

### Task fails to start (STOPPED immediately)

```bash
# Get stopped task ARN
aws ecs list-tasks --cluster tenant-admin-cluster --desired-status STOPPED

# Describe the stopped task for stop reason
aws ecs describe-tasks --cluster tenant-admin-cluster --tasks <TASK_ARN>
```

Common causes:
- **Image pull failure**: Check ECR permissions on `ecsTaskExecutionRole`.
- **Port conflict**: Ensure security group allows inbound on 8080/8081.
- **OOM killed**: Increase `memory` in task definition (current: 1024 MB).
- **Missing SSM parameter**: Verify all `/tenant-admin/*` parameters exist.

### Service not reaching desired count

```bash
aws ecs describe-services \
  --cluster tenant-admin-cluster \
  --services tenant-admin-service \
  --query "services[0].events[:5]"
```

### Health check failures

- Verify the health endpoint responds: `curl http://<TASK_IP>:8081/health`
- Check JVM startup time – the task definition uses `startPeriod: 60s`.
- Ensure security group allows health-check traffic on port 8081.

### CPU / Memory errors

```
InvalidParameterException: Invalid CPU or Memory value
```
Use only valid Fargate combinations (see table above). Default is cpu=512, memory=1024.

### Network connectivity issues

- Ensure subnets have a route to the internet (NAT Gateway for private, IGW for public).
- Verify VPC endpoints for ECR (`com.amazonaws.region.ecr.api`, `com.amazonaws.region.ecr.dkr`) and CloudWatch Logs if using private subnets without NAT.

---

## ECS Fargate Scaling & Management

### Manual scaling

```bash
aws ecs update-service \
  --cluster tenant-admin-cluster \
  --service tenant-admin-service \
  --desired-count 4
```

### Auto Scaling

```bash
# Register scalable target
aws application-autoscaling register-scalable-target \
  --service-namespace ecs \
  --resource-id service/tenant-admin-cluster/tenant-admin-service \
  --scalable-dimension ecs:service:DesiredCount \
  --min-capacity 2 \
  --max-capacity 10

# CPU-based scaling policy
aws application-autoscaling put-scaling-policy \
  --service-namespace ecs \
  --resource-id service/tenant-admin-cluster/tenant-admin-service \
  --scalable-dimension ecs:service:DesiredCount \
  --policy-name tenant-admin-cpu-scaling \
  --policy-type TargetTrackingScaling \
  --target-tracking-scaling-policy-configuration '{
    "TargetValue": 70.0,
    "PredefinedMetricSpecification": {
      "PredefinedMetricType": "ECSServiceAverageCPUUtilization"
    },
    "ScaleInCooldown": 300,
    "ScaleOutCooldown": 60
  }'
```

### Blue/Green Deployment (CodeDeploy)

1. Enable `deploymentController: {type: CODE_DEPLOY}` in the service definition.
2. Create a CodeDeploy application and deployment group targeting the ECS service.
3. Use `appspec.yaml` with `TaskDefinition` and `LoadBalancerInfo` sections.

### Force new deployment (rolling restart)

```bash
aws ecs update-service \
  --cluster tenant-admin-cluster \
  --service tenant-admin-service \
  --force-new-deployment
```

---

## Configuration Management

### Environment Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8080` | Application HTTP port |
| `HEALTH_CHECK_PORT` | `8081` | Health endpoint port |
| `CONFIG_FILE_PATH` | `/opt/app/config/app.properties` | External config file |
| `LOG_FILE_PATH` | `/var/log/mini-app/mini-app.log` | Log file path |
| `LOG_DIR` | `/var/log/mini-app` | Log directory |
| `DB_URL` | — | JDBC connection URL (SSM secret) |
| `DB_USERNAME` | — | Database username (SSM secret) |
| `DB_PASSWORD` | — | Database password (SSM secret) |
| `REDIS_HOST` | — | Redis hostname (SSM secret) |
| `REDIS_PORT` | `6379` | Redis port (SSM secret) |
| `JAVA_OPTS` | `-Xmx512m -Xms256m ...` | JVM tuning flags |
| `TZ` | `UTC` | Container timezone |

### Updating configuration without redeployment

Update SSM parameters and force a new deployment:

```bash
aws ssm put-parameter --name /tenant-admin/DB_PASSWORD --value "newpassword" --type SecureString --overwrite
aws ecs update-service --cluster tenant-admin-cluster --service tenant-admin-service --force-new-deployment
```

---

## Security Considerations

1. **Non-root container**: The Dockerfile creates and uses `appuser:appgroup`.
2. **Secrets management**: Use AWS SSM Parameter Store (SecureString) or AWS Secrets Manager – never hardcode secrets.
3. **Least-privilege IAM**: Grant only the permissions the task actually needs.
4. **Private subnets**: Deploy tasks in private subnets behind an ALB for production.
5. **Security groups**: Restrict inbound to ALB security group only; restrict outbound to required services.
6. **Image scanning**: Enable ECR image scanning on push: `aws ecr put-image-scanning-configuration --repository-name tenant-admin --image-scanning-configuration scanOnPush=true`.
7. **VPC endpoints**: Use VPC endpoints for ECR and CloudWatch Logs to avoid traffic traversing the public internet.
8. **Encryption in transit**: Use HTTPS on the ALB listener with an ACM certificate.

---

## Java-Specific Notes

### JVM Tuning for Containers

The `JAVA_OPTS` environment variable is pre-configured with container-aware flags:

```
-Xmx512m                        # Maximum heap (matches container memory limit)
-Xms256m                        # Initial heap
-XX:+UseContainerSupport        # Respect cgroup memory limits (Java 11+)
-XX:MaxRAMPercentage=75.0       # Use 75% of container RAM for heap
-XX:+UnlockExperimentalVMOptions
-Djava.security.egd=file:/dev/./urandom  # Faster SecureRandom
-Dfile.encoding=UTF-8
-Duser.timezone=UTC
```

Override `JAVA_OPTS` in the task definition environment to tune for your workload.

### JVM Startup Time

Java 11 applications typically take 10–30 seconds to start. The ECS health check grace period is set to 60 seconds to accommodate this. If your application takes longer, increase `startPeriod` in the task definition.

### Garbage Collection

For low-latency workloads, consider adding G1GC flags:
```
-XX:+UseG1GC -XX:MaxGCPauseMillis=200
```

For throughput-oriented workloads:
```
-XX:+UseParallelGC
```

### Heap Dump on OOM

Add to `JAVA_OPTS` for diagnostics:
```
-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heapdump.hprof
```

### Graceful Shutdown

The Dockerfile sets `STOPSIGNAL SIGTERM`. ECS sends `SIGTERM` before `SIGKILL` (default 30-second grace period). Ensure your application handles `SIGTERM` for graceful shutdown.

To increase the stop timeout:
```json
"stopTimeout": 60
```
Add this to the container definition in `task-definition.json`.
