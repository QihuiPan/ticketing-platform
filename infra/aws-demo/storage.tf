resource "aws_s3_bucket" "deployment" {
  bucket_prefix = "${local.name}-artifacts-"
  force_destroy = true
}

resource "aws_s3_bucket_public_access_block" "deployment" {
  bucket = aws_s3_bucket.deployment.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "deployment" {
  bucket = aws_s3_bucket.deployment.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "deployment" {
  bucket = aws_s3_bucket.deployment.id

  rule {
    id     = "expire-deployment-artifacts"
    status = "Enabled"

    filter {
      prefix = "releases/"
    }

    expiration {
      days = var.deployment_artifact_retention_days
    }
  }
}
