# CrowdRewards AWS infrastructure

Single-server hackathon architecture: Amazon Linux 2023, EC2 t3.small, encrypted 20 GiB gp3 root EBS, Nginx, Java 21 / Spring Boot, React/Vite, SQLite.

## Isolation from DealSeal

This stack **reuses only the existing DealSeal VPC** `vpc-019341a148043da19` and public subnet `subnet-04b32832073263b34` in `us-west-2`. It creates a **new CrowdRewards-only EC2 instance, security group, IAM role and instance profile**. It does not manage DealSeal EC2 resources. Use a separate Terraform state directory for CrowdRewards; never run Terraform against DealSeal state.

## Phase 1: AWS resources

Prerequisites: AWS credentials configured locally; Terraform >= 1.6; an existing VPC and **public subnet** with a route to an Internet Gateway. Check EC2 t3.small pricing and EBS costs for your chosen region.

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars
# VPC, subnet, and region are prefilled for the existing DealSeal network.\n# Replace http_allowed_cidr with your current trusted public IP /32
terraform init
terraform fmt -check
terraform validate
terraform plan\n# Verify the plan creates only new CrowdRewards resources and changes no DealSeal resources
# Review resources and cost before deliberately applying:
terraform apply
```

The public HTTP port is restricted to a trusted CIDR by default. Do NOT open this unauthenticated merchant demo to 0.0.0.0/0. SSH is disabled by default; IAM grants the EC2 instance Session Manager permissions, subject to SSM agent/endpoint/network availability.

Session Manager example:
```bash
aws ssm start-session --target "$(terraform output -raw instance_id)"
```

The EBS root disk has **delete_on_termination=false**. Destroying the instance may leave a billable EBS volume that must be located and deleted manually after preserving needed SQLite data. Terraform local state can include sensitive infrastructure metadata: never commit state or secrets.

## Next phase

Install Java 21 and Nginx, deploy backend/frontend, configure a systemd unit and SQLite persistence, configure private PayPal/OpenAI credentials on the server, and establish TLS and the matching PayPal callback URL before customer checkout tests. This Terraform configuration provisions only AWS infrastructure and does not deploy application code or DNS/HTTPS.
