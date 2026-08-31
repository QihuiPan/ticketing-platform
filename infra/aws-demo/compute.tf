data "aws_ssm_parameter" "amazon_linux_2023" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

resource "aws_iam_role" "instance" {
  name = "${local.name}-instance"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.instance.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy" "deployment_artifacts" {
  name = "read-deployment-artifacts"
  role = aws_iam_role.instance.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["s3:GetObject"]
        Resource = "${aws_s3_bucket.deployment.arn}/releases/*"
      },
      {
        Effect   = "Allow"
        Action   = ["s3:ListBucket"]
        Resource = aws_s3_bucket.deployment.arn
        Condition = {
          StringLike = { "s3:prefix" = ["releases/*"] }
        }
      }
    ]
  })
}

resource "aws_iam_instance_profile" "demo" {
  name = "${local.name}-instance"
  role = aws_iam_role.instance.name
}

resource "aws_instance" "demo" {
  ami                         = data.aws_ssm_parameter.amazon_linux_2023.value
  instance_type               = var.instance_type
  subnet_id                   = aws_subnet.public.id
  vpc_security_group_ids      = [aws_security_group.demo.id]
  associate_public_ip_address = true
  iam_instance_profile        = aws_iam_instance_profile.demo.name

  user_data = templatefile("${path.module}/user-data.sh.tftpl", {
    site_address = var.site_address
    seed_demo    = tostring(var.seed_demo)
  })
  user_data_replace_on_change = true

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
  }

  root_block_device {
    encrypted             = true
    volume_type           = "gp3"
    volume_size           = var.root_volume_size_gib
    delete_on_termination = true
  }

  credit_specification {
    cpu_credits = "standard"
  }

  tags = { Name = local.name }

  depends_on = [aws_iam_role_policy_attachment.ssm]
}

resource "aws_eip" "demo" {
  domain   = "vpc"
  instance = aws_instance.demo.id

  tags = { Name = "${local.name}-public" }

  depends_on = [aws_internet_gateway.demo]
}
