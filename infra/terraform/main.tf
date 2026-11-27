terraform {
  required_version = ">= 1.5"
  required_providers {
    oci = { source = "oracle/oci", version = "~> 7.0" }
  }
}
provider "oci" { region = var.region }
variable "region" { type = string }
variable "compartment_id" { type = string }
variable "availability_domain" { type = string }
variable "ubuntu_arm_image_id" { type = string }
variable "ssh_public_key" { type = string }
variable "administrator_cidr" {
  type        = string
  description = "Your administrator IP in /32 notation. Never expose SSH to the world."
  validation {
    condition     = can(cidrnetmask(var.administrator_cidr)) && endswith(var.administrator_cidr, "/32")
    error_message = "Use one trusted administrator IPv4 address with /32."
  }
}
resource "oci_core_vcn" "app" {
  compartment_id = var.compartment_id
  cidr_block     = "10.20.0.0/16"
  display_name   = "answerbase"
}
resource "oci_core_internet_gateway" "app" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.app.id
  enabled        = true
}
resource "oci_core_route_table" "app" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.app.id
  route_rules {
    network_entity_id = oci_core_internet_gateway.app.id
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
  }
}
resource "oci_core_security_list" "app" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.app.id
  egress_security_rules {
    protocol    = "all"
    destination = "0.0.0.0/0"
  }
  dynamic "ingress_security_rules" {
    for_each = toset([80, 443])
    content {
      protocol = "6"
      source   = "0.0.0.0/0"
      tcp_options {
        min = ingress_security_rules.value
        max = ingress_security_rules.value
      }
    }
  }
  ingress_security_rules {
    protocol = "6"
    source   = var.administrator_cidr
    tcp_options {
      min = 22
      max = 22
    }
  }
}
resource "oci_core_subnet" "app" {
  compartment_id    = var.compartment_id
  vcn_id            = oci_core_vcn.app.id
  cidr_block        = "10.20.1.0/24"
  route_table_id    = oci_core_route_table.app.id
  security_list_ids = [oci_core_security_list.app.id]
}
resource "oci_core_instance" "app" {
  compartment_id      = var.compartment_id
  availability_domain = var.availability_domain
  display_name        = "answerbase"
  shape               = "VM.Standard.A1.Flex"
  shape_config {
    ocpus         = 2
    memory_in_gbs = 12
  }
  source_details {
    source_type             = "image"
    source_id               = var.ubuntu_arm_image_id
    boot_volume_size_in_gbs = 50
  }
  create_vnic_details {
    subnet_id        = oci_core_subnet.app.id
    assign_public_ip = true
  }
  metadata = {
    ssh_authorized_keys = var.ssh_public_key
    user_data           = base64encode(file("${path.module}/cloud-init.yml"))
  }
  lifecycle { prevent_destroy = true }
}
output "public_ip" { value = oci_core_instance.app.public_ip }
