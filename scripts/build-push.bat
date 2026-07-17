@echo off
setlocal enabledelayedexpansion

REM =============================================================================
REM build-push.bat - Build and push Docker image for Tenant Admin
REM Usage: scripts\build-push.bat
REM Run from the repository root directory.
REM =============================================================================

set PROJECT_NAME=tenant-admin
set DOCKERFILE_PATH=Dockerfile

echo ==============================================
echo   Tenant Admin - Docker Build ^& Push
echo ==============================================

REM ---------------------------------------------------------------------------
REM Sanitize image name using PowerShell
REM ---------------------------------------------------------------------------
for /f "delims=" %%i in ('powershell -NoProfile -Command "$n = 'tenant-admin'; $n = $n.ToLower() -replace '[^a-z0-9]','-'; $n = $n.Trim('-'); Write-Output $n"') do set IMAGE_NAME=%%i
echo Image name: !IMAGE_NAME!

REM ---------------------------------------------------------------------------
REM Prompt for image tag
REM ---------------------------------------------------------------------------
set /p INPUT_TAG="Enter image tag [latest]: "
if "!INPUT_TAG!"=="" set INPUT_TAG=latest
for /f "delims=" %%i in ('powershell -NoProfile -Command "$t = '!INPUT_TAG!'; $t = $t.ToLower() -replace '[^a-z0-9._-]','-'; $t = $t.Trim('-'); if ($t -eq '') { $t = 'latest' }; Write-Output $t"') do set IMAGE_TAG=%%i
echo Image tag: !IMAGE_TAG!

REM ---------------------------------------------------------------------------
REM Registry selection
REM ---------------------------------------------------------------------------
echo.
echo Select container registry:
echo   1. AWS ECR
echo   2. Docker Hub
set /p REGISTRY_CHOICE="Enter choice [1]: "
if "!REGISTRY_CHOICE!"=="" set REGISTRY_CHOICE=1

REM ---------------------------------------------------------------------------
REM Registry-specific configuration
REM ---------------------------------------------------------------------------
if "!REGISTRY_CHOICE!"=="1" (
    REM ---- AWS ECR ----
    echo.
    echo --- AWS ECR Configuration ---
    set /p AWS_REGION="Enter AWS Region [us-east-1]: "
    if "!AWS_REGION!"=="" set AWS_REGION=us-east-1

    set /p AWS_ACCOUNT_ID="Enter AWS Account ID (leave blank to auto-detect): "
    if "!AWS_ACCOUNT_ID!"=="" (
        echo Fetching AWS Account ID...
        for /f "delims=" %%a in ('aws sts get-caller-identity --query Account --output text') do set AWS_ACCOUNT_ID=%%a
        echo Account ID: !AWS_ACCOUNT_ID!
    )

    set /p ECR_REPO="Enter ECR repository name [!IMAGE_NAME!]: "
    if "!ECR_REPO!"=="" set ECR_REPO=!IMAGE_NAME!

    set REGISTRY_URL=!AWS_ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com
    set FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!

    echo.
    echo Logging in to ECR...
    aws ecr get-login-password --region !AWS_REGION! | docker login --username AWS --password-stdin !REGISTRY_URL!
    if !ERRORLEVEL! neq 0 (
        echo ECR login failed.
        exit /b 1
    )

    echo Ensuring ECR repository exists...
    aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
    if !ERRORLEVEL! neq 0 (
        echo Creating ECR repository: !ECR_REPO!
        aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
        if !ERRORLEVEL! neq 0 (
            echo Failed to create ECR repository.
            exit /b 1
        )
    )
    echo ECR repository ready: !ECR_REPO!

) else if "!REGISTRY_CHOICE!"=="2" (
    REM ---- Docker Hub ----
    echo.
    echo --- Docker Hub Configuration ---
    set /p DOCKER_USERNAME="Enter Docker Hub username: "
    set /p DOCKER_PASSWORD="Enter Docker Hub password/token: "
    set /p DOCKER_REPO="Enter Docker Hub repository [!DOCKER_USERNAME!/!IMAGE_NAME!]: "
    if "!DOCKER_REPO!"=="" set DOCKER_REPO=!DOCKER_USERNAME!/!IMAGE_NAME!

    set FULL_IMAGE_NAME=!DOCKER_REPO!:!IMAGE_TAG!

    echo Logging in to Docker Hub...
    echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
    if !ERRORLEVEL! neq 0 (
        echo Docker Hub login failed.
        exit /b 1
    )

) else (
    echo Invalid registry choice. Exiting.
    exit /b 1
)

echo.
echo Full image name: !FULL_IMAGE_NAME!

REM ---------------------------------------------------------------------------
REM Build Docker image (build context is repository root)
REM ---------------------------------------------------------------------------
echo.
echo Building Docker image...
docker build -f !DOCKERFILE_PATH! -t !FULL_IMAGE_NAME! .
if !ERRORLEVEL! neq 0 (
    echo Docker build failed.
    exit /b 1
)
echo Docker image built successfully.

REM ---------------------------------------------------------------------------
REM Push Docker image
REM ---------------------------------------------------------------------------
echo.
echo Pushing Docker image to registry...
docker push !FULL_IMAGE_NAME!
if !ERRORLEVEL! neq 0 (
    echo Docker push failed.
    exit /b 1
)
echo Image pushed successfully: !FULL_IMAGE_NAME!

echo.
echo ==============================================
echo   Build ^& Push Complete!
echo   Image: !FULL_IMAGE_NAME!
echo ==============================================

endlocal
