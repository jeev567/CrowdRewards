variable "aws_region" {
  description = "AWS region in which to run the hackathon demo."
  type        = string
  default     = "us-west-2"
}

variable "instance_type" {
  description = "EC2 size."
  type        = string
  default     = "t3.small"
}

variable "root_volume_gb" {
  description = "Persistent root EBS volume size."
  type        = number
  default     = 20
}

variable "ssh_public_key" {
  description = "Your SSH PUBLIC key only, never a private key."
  type        = string
  default     = ""
  sensitive   = true
}

variable "ssh_allowed_cidr" {
  description = "Optional trusted IP/CIDR for SSH, e.g. 203.0.113.1/32. Empty disables SSH inbound; use Session Manager."
  type        = string
  default     = ""
}

variable "http_allowed_cidr" {
  description = "Trusted public IP/CIDR for HTTP during an isolated demo. HTTPS requires a domain and TLS configuration."
  type        = string
  default     = "127.0.0.1/32"
}

variable "subnet_id" {
  description = "Public subnet ID in an existing VPC, with automatic public IP assignment possible."
  type        = string
  default     = "subnet-04b32832073263b34"
}

variable "vpc_id" {
  description = "Existing DealSeal VPC shared by CrowdRewards networking only."
  type        = string
  default     = "vpc-019341a148043da19"
}
