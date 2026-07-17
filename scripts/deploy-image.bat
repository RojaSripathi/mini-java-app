@echo off
setlocal enabledelayedexpansion

REM =============================================================================
REM deploy-image.bat - Deploy Tenant Admin to AWS ECS Fargate (Windows)
REM Usage: scripts\deploy-image.bat
REM Prerequisites: aws-cli v2 configured with appropriate IAM permissions
REM =============================================================================

set SERVICE_NAME=tenant-admin-service
set TASK_FAMILY=tenant-admin-task
set CONTAINER_NAME=tenant-admin
set APP_PORT=8080
set HEALTH_PORT=8081
set LOG_GROUP=/ecs/tenant-admin
set TASK_DEF_FILE=ecs\task-definition.json
set SERVICE_DEF_FILE=ecs\service-definition.json

echo ==============================================
echo   Tenant Admin - ECS Fargate Deployment
echo ==============================================

REM ---------------------------------------------------------------------------
REM Collect deployment parameters
REM ---------------------------------------------------------------------------
set /p AWS_REGION="Enter AWS Region [us-east-1]: "
if "!AWS_REGION!"=="" set AWS_REGION=us-east-1

set /p CLUSTER_NAME="Enter ECS Cluster name [tenant-admin-cluster]: "
if "!CLUSTER_NAME!"=="" set CLUSTER_NAME=tenant-admin-cluster

set /p IMAGE_URI="Enter ECR Image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/tenant-admin:latest): "
if "!IMAGE_URI!"=="" (
    echo ERROR: Image URI is required.
    exit /b 1
)

set /p VPC_ID="Enter VPC ID: "
if "!VPC_ID!"=="" (
    echo ERROR: VPC ID is required.
    exit /b 1
)

set /p SUBNETS_INPUT="Enter Subnet IDs (comma-separated, e.g. subnet-aaa,subnet-bbb): "
if "!SUBNETS_INPUT!"=="" (
    echo ERROR: At least one subnet is required.
    exit /b 1
)

REM Parse first two subnets
for /f "tokens=1,2 delims=," %%a in ("!SUBNETS_INPUT!") do (
    set SUBNET_1=%%a
    set SUBNET_2=%%b
)
if "!SUBNET_2!"=="" set SUBNET_2=!SUBNET_1!

set /p SECURITY_GROUP="Enter Security Group ID: "
if "!SECURITY_GROUP!"=="" (
    echo ERROR: Security Group ID is required.
    exit /b 1
)

REM ---------------------------------------------------------------------------
REM Resolve AWS Account ID
REM ---------------------------------------------------------------------------
echo.
echo Resolving AWS Account ID...
for /f "delims=" %%a in ('aws sts get-caller-identity --query Account --output text') do set ACCOUNT_ID=%%a
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to get AWS Account ID. Check your AWS credentials.
    exit /b 1
)
echo Account ID: !ACCOUNT_ID!

REM ---------------------------------------------------------------------------
REM Ensure CloudWatch log group exists
REM ---------------------------------------------------------------------------
echo.
echo Ensuring CloudWatch log group exists: !LOG_GROUP!
aws logs create-log-group --log-group-name "!LOG_GROUP!" --region !AWS_REGION! >nul 2>&1
echo Log group ready.

REM ---------------------------------------------------------------------------
REM Ensure ECS cluster exists
REM ---------------------------------------------------------------------------
echo.
echo Checking ECS cluster: !CLUSTER_NAME!
for /f "delims=" %%s in ('aws ecs describe-clusters --clusters !CLUSTER_NAME! --region !AWS_REGION! --query "clusters[0].status" --output text 2^>nul') do set CLUSTER_STATUS=%%s
if "!CLUSTER_STATUS!" neq "ACTIVE" (
    echo Creating ECS cluster: !CLUSTER_NAME!
    aws ecs create-cluster --cluster-name !CLUSTER_NAME! --region !AWS_REGION!
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Failed to create ECS cluster.
        exit /b 1
    )
)
echo Cluster ready: !CLUSTER_NAME!

REM ---------------------------------------------------------------------------
REM Load balancer (optional)
REM ---------------------------------------------------------------------------
echo.
set /p NEED_LB="Do you need an Application Load Balancer for this service? (y/n) [n]: "
if "!NEED_LB!"=="" set NEED_LB=n

set TARGET_GROUP_ARN=
set ALB_DNS=

if /i "!NEED_LB!"=="y" (
    echo.
    echo Creating Application Load Balancer...

    set ALB_NAME=tenant-admin-alb
    set TG_NAME=tenant-admin-tg

    for /f "delims=" %%a in ('aws elbv2 create-load-balancer --name !ALB_NAME! --subnets !SUBNET_1! !SUBNET_2! --security-groups !SECURITY_GROUP! --scheme internet-facing --type application --region !AWS_REGION! --query "LoadBalancers[0].LoadBalancerArn" --output text') do set ALB_ARN=%%a
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Failed to create ALB.
        exit /b 1
    )
    echo ALB created: !ALB_ARN!

    for /f "delims=" %%d in ('aws elbv2 describe-load-balancers --load-balancer-arns !ALB_ARN! --region !AWS_REGION! --query "LoadBalancers[0].DNSName" --output text') do set ALB_DNS=%%d

    for /f "delims=" %%t in ('aws elbv2 create-target-group --name !TG_NAME! --protocol HTTP --port !APP_PORT! --vpc-id !VPC_ID! --target-type ip --health-check-path "/health" --health-check-port !HEALTH_PORT! --region !AWS_REGION! --query "TargetGroups[0].TargetGroupArn" --output text') do set TARGET_GROUP_ARN=%%t
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Failed to create Target Group.
        exit /b 1
    )
    echo Target Group created: !TARGET_GROUP_ARN!

    aws elbv2 create-listener --load-balancer-arn !ALB_ARN! --protocol HTTP --port 80 --default-actions "Type=forward,TargetGroupArn=!TARGET_GROUP_ARN!" --region !AWS_REGION! >nul
    echo ALB listener created on port 80.
)

REM ---------------------------------------------------------------------------
REM Prepare task definition (replace placeholders using PowerShell)
REM ---------------------------------------------------------------------------
echo.
echo Preparing task definition...
powershell -NoProfile -Command ^
    "(Get-Content '!TASK_DEF_FILE!') -replace '{{ACCOUNT_ID}}','!ACCOUNT_ID!' -replace '{{AWS_REGION}}','!AWS_REGION!' -replace '{{IMAGE_URI}}','!IMAGE_URI!' | Set-Content '%TEMP%\task-definition-deploy.json'"
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to prepare task definition.
    exit /b 1
)

REM ---------------------------------------------------------------------------
REM Register task definition
REM ---------------------------------------------------------------------------
echo Registering task definition...
for /f "delims=" %%a in ('aws ecs register-task-definition --cli-input-json file://%TEMP%\task-definition-deploy.json --region !AWS_REGION! --query "taskDefinition.taskDefinitionArn" --output text') do set TASK_DEF_ARN=%%a
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to register task definition.
    exit /b 1
)
echo Task definition registered: !TASK_DEF_ARN!

REM ---------------------------------------------------------------------------
REM Prepare service definition (replace placeholders using PowerShell)
REM ---------------------------------------------------------------------------
echo.
echo Preparing service definition...
powershell -NoProfile -Command ^
    "(Get-Content '!SERVICE_DEF_FILE!') -replace '{{CLUSTER_NAME}}','!CLUSTER_NAME!' -replace '{{SUBNET_1}}','!SUBNET_1!' -replace '{{SUBNET_2}}','!SUBNET_2!' -replace '{{SECURITY_GROUP}}','!SECURITY_GROUP!' | Set-Content '%TEMP%\service-definition-deploy.json'"
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to prepare service definition.
    exit /b 1
)

REM Inject load balancer config if needed
if /i "!NEED_LB!"=="y" (
    if "!TARGET_GROUP_ARN!" neq "" (
        powershell -NoProfile -Command ^
            "$svc = Get-Content '%TEMP%\service-definition-deploy.json' | ConvertFrom-Json; $lb = @{targetGroupArn='!TARGET_GROUP_ARN!'; containerName='!CONTAINER_NAME!'; containerPort=!APP_PORT!}; $svc | Add-Member -NotePropertyName 'loadBalancers' -NotePropertyValue @($lb) -Force; $svc | Add-Member -NotePropertyName 'healthCheckGracePeriodSeconds' -NotePropertyValue 300 -Force; $svc | ConvertTo-Json -Depth 10 | Set-Content '%TEMP%\service-definition-deploy.json'"
        echo Load balancer configuration injected.
    )
)

REM ---------------------------------------------------------------------------
REM Create or update ECS service
REM ---------------------------------------------------------------------------
echo.
echo Checking if ECS service exists...
for /f "delims=" %%s in ('aws ecs describe-services --cluster !CLUSTER_NAME! --services !SERVICE_NAME! --region !AWS_REGION! --query "services[?status!='INACTIVE'].serviceName" --output text 2^>nul') do set EXISTING_SERVICE=%%s

if "!EXISTING_SERVICE!"=="" (
    echo Creating new ECS service: !SERVICE_NAME!
    aws ecs create-service --cli-input-json file://%TEMP%\service-definition-deploy.json --region !AWS_REGION!
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Failed to create ECS service.
        exit /b 1
    )
    echo Service created.
) else (
    echo Updating existing ECS service: !SERVICE_NAME!
    aws ecs update-service --cluster !CLUSTER_NAME! --service !SERVICE_NAME! --task-definition !TASK_DEF_ARN! --region !AWS_REGION! >nul
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Failed to update ECS service.
        exit /b 1
    )
    echo Service updated.
)

REM ---------------------------------------------------------------------------
REM Wait for service stability
REM ---------------------------------------------------------------------------
echo.
echo Waiting for service to become stable (this may take a few minutes)...
aws ecs wait services-stable --cluster !CLUSTER_NAME! --services !SERVICE_NAME! --region !AWS_REGION!
if !ERRORLEVEL! neq 0 (
    echo WARNING: Service did not stabilize within the expected time. Check ECS console.
)
echo Service is stable.

REM ---------------------------------------------------------------------------
REM Verify deployment
REM ---------------------------------------------------------------------------
echo.
echo Verifying deployment...
aws ecs describe-services --cluster !CLUSTER_NAME! --services !SERVICE_NAME! --region !AWS_REGION! --query "services[0].{Status:status,Running:runningCount,Desired:desiredCount,Pending:pendingCount}"

echo.
echo ==============================================
echo   Deployment Complete!
echo   Cluster:      !CLUSTER_NAME!
echo   Service:      !SERVICE_NAME!
echo   Task Def ARN: !TASK_DEF_ARN!
echo   CloudWatch:   !LOG_GROUP!
if /i "!NEED_LB!"=="y" (
    echo   ALB DNS:      http://!ALB_DNS!
)
echo ==============================================
echo.
echo Troubleshooting tips:
echo   - View logs:  aws logs tail !LOG_GROUP! --follow --region !AWS_REGION!
echo   - List tasks: aws ecs list-tasks --cluster !CLUSTER_NAME! --service-name !SERVICE_NAME! --region !AWS_REGION!

endlocal
