#!/bin/bash
# =============================================================================
# build-push.sh – Build and push Docker image for Tenant Admin
# Usage: ./scripts/build-push.sh
# Run from the repository root directory.
# =============================================================================
set -e
set -o pipefail

PROJECT_NAME="tenant-admin"
DOCKERFILE_PATH="Dockerfile"

echo "=============================================="
echo "  Tenant Admin – Docker Build & Push"
echo "=============================================="

# ---------------------------------------------------------------------------
# Sanitize image name: lowercase, replace non-alphanumeric with hyphens,
# trim leading/trailing hyphens.
# ---------------------------------------------------------------------------
IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')
echo "Image name: $IMAGE_NAME"

# ---------------------------------------------------------------------------
# Prompt for image tag
# ---------------------------------------------------------------------------
read -rp "Enter image tag [latest]: " INPUT_TAG
INPUT_TAG=$(echo "${INPUT_TAG:-latest}" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9._-' '-' | sed 's/^-*//;s/-*$//')
IMAGE_TAG="${INPUT_TAG:-latest}"
echo "Image tag: $IMAGE_TAG"

# ---------------------------------------------------------------------------
# Registry selection
# ---------------------------------------------------------------------------
echo ""
echo "Select container registry:"
echo "  1. AWS ECR"
echo "  2. Docker Hub"
read -rp "Enter choice [1]: " REGISTRY_CHOICE
REGISTRY_CHOICE="${REGISTRY_CHOICE:-1}"

# ---------------------------------------------------------------------------
# Registry-specific configuration
# ---------------------------------------------------------------------------
if [ "$REGISTRY_CHOICE" = "1" ]; then
    # ---- AWS ECR ----
    echo ""
    echo "--- AWS ECR Configuration ---"
    read -rp "Enter AWS Region [us-east-1]: " AWS_REGION
    AWS_REGION="${AWS_REGION:-us-east-1}"

    read -rp "Enter AWS Account ID: " AWS_ACCOUNT_ID
    if [ -z "$AWS_ACCOUNT_ID" ]; then
        echo "Fetching AWS Account ID..."
        AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
        echo "Account ID: $AWS_ACCOUNT_ID"
    fi

    read -rp "Enter ECR repository name [$IMAGE_NAME]: " ECR_REPO
    ECR_REPO="${ECR_REPO:-$IMAGE_NAME}"

    REGISTRY_URL="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
    FULL_IMAGE_NAME="${REGISTRY_URL}/${ECR_REPO}:${IMAGE_TAG}"

    echo ""
    echo "Logging in to ECR..."
    aws ecr get-login-password --region "$AWS_REGION" | \
        docker login --username AWS --password-stdin "$REGISTRY_URL"

    echo "Ensuring ECR repository exists..."
    aws ecr describe-repositories --repository-names "$ECR_REPO" --region "$AWS_REGION" >/dev/null 2>&1 || \
        aws ecr create-repository --repository-name "$ECR_REPO" --region "$AWS_REGION"
    echo "ECR repository ready: $ECR_REPO"

elif [ "$REGISTRY_CHOICE" = "2" ]; then
    # ---- Docker Hub ----
    echo ""
    echo "--- Docker Hub Configuration ---"
    read -rp "Enter Docker Hub username: " DOCKER_USERNAME
    read -rsp "Enter Docker Hub password/token: " DOCKER_PASSWORD
    echo ""
    read -rp "Enter Docker Hub repository [$DOCKER_USERNAME/$IMAGE_NAME]: " DOCKER_REPO
    DOCKER_REPO="${DOCKER_REPO:-$DOCKER_USERNAME/$IMAGE_NAME}"

    FULL_IMAGE_NAME="${DOCKER_REPO}:${IMAGE_TAG}"

    echo "Logging in to Docker Hub..."
    echo "$DOCKER_PASSWORD" | docker login --username "$DOCKER_USERNAME" --password-stdin
else
    echo "Invalid registry choice. Exiting."
    exit 1
fi

echo ""
echo "Full image name: $FULL_IMAGE_NAME"

# ---------------------------------------------------------------------------
# Build Docker image (build context is repository root)
# ---------------------------------------------------------------------------
echo ""
echo "Building Docker image..."
docker build -f "$DOCKERFILE_PATH" -t "$FULL_IMAGE_NAME" .
echo "Docker image built successfully."

# Also tag as latest for convenience
if [ "$IMAGE_TAG" != "latest" ]; then
    if [ "$REGISTRY_CHOICE" = "1" ]; then
        docker tag "$FULL_IMAGE_NAME" "${REGISTRY_URL}/${ECR_REPO}:latest"
    else
        docker tag "$FULL_IMAGE_NAME" "${DOCKER_REPO}:latest"
    fi
fi

# ---------------------------------------------------------------------------
# Push Docker image
# ---------------------------------------------------------------------------
echo ""
echo "Pushing Docker image to registry..."
docker push "$FULL_IMAGE_NAME"
echo "Image pushed successfully: $FULL_IMAGE_NAME"

if [ "$IMAGE_TAG" != "latest" ]; then
    if [ "$REGISTRY_CHOICE" = "1" ]; then
        docker push "${REGISTRY_URL}/${ECR_REPO}:latest"
    else
        docker push "${DOCKER_REPO}:latest"
    fi
    echo "Latest tag also pushed."
fi

echo ""
echo "=============================================="
echo "  Build & Push Complete!"
echo "  Image: $FULL_IMAGE_NAME"
echo "=============================================="
