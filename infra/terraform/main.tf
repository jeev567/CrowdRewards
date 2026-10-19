data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]
  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }
  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

resource "aws_security_group" "demo" {
  name_prefix = "crowdrewards-demo-"
  description = "CrowdRewards only - separate from DealSeal security groups"
  vpc_id      = var.vpc_id


  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_iam_role" "instance" {
  name_prefix = "crowdrewards-demo-"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.instance.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "instance" {
  name_prefix = "crowdrewards-demo-"
  role        = aws_iam_role.instance.name
}

resource "aws_key_pair" "optional" {
  count      = var.ssh_public_key == "" ? 0 : 1
  key_name   = "crowdrewards-demo"
  public_key = var.ssh_public_key
}

resource "aws_instance" "demo" {
  ami                         = data.aws_ami.al2023.id
  instance_type               = var.instance_type
  subnet_id                   = var.subnet_id
  associate_public_ip_address = true
  vpc_security_group_ids      = [aws_security_group.demo.id]
  iam_instance_profile        = aws_iam_instance_profile.instance.name
  key_name                    = var.ssh_public_key == "" ? null : aws_key_pair.optional[0].key_name

  metadata_options {
    http_tokens = "required"
  }

  root_block_device {
    volume_size           = var.root_volume_gb
    volume_type           = "gp3"
    encrypted             = true
    delete_on_termination = false
  }

  tags = { Name = "crowdrewards-hackathon-demo", Application = "CrowdRewards" }
}

# Standalone rules avoid conflicts with inline security group rule management.
resource "aws_vpc_security_group_ingress_rule" "http" {
  security_group_id = aws_security_group.demo.id
  description       = "HTTP for Let's Encrypt HTTP-01 validation"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
  cidr_ipv4         = var.http_allowed_cidr
}

resource "aws_vpc_security_group_ingress_rule" "https" {
  security_group_id = aws_security_group.demo.id
  description       = "HTTPS from trusted IP only"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  cidr_ipv4         = var.https_allowed_cidr
}

resource "aws_vpc_security_group_egress_rule" "all" {
  security_group_id = aws_security_group.demo.id
  description       = "Outbound APIs and package installation"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_ingress_rule" "ssh" {
  count             = var.ssh_allowed_cidr == "" ? 0 : 1
  security_group_id = aws_security_group.demo.id
  description       = "Optional SSH from trusted address"
  ip_protocol       = "tcp"
  from_port         = 22
  to_port           = 22
  cidr_ipv4         = var.ssh_allowed_cidr
}
