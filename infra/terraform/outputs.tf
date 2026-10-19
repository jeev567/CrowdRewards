output "instance_id" {
  description = "EC2 instance ID for Systems Manager Session Manager."
  value       = aws_instance.demo.id
}

output "public_ip" {
  description = "Ephemeral public address; may change on instance restart."
  value       = aws_instance.demo.public_ip
}

output "public_dns" {
  value = aws_instance.demo.public_dns
}
