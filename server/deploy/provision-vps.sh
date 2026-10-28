#!/usr/bin/env bash
set -euo pipefail
if [ "$(id -u)" -ne 0 ]; then printf '%s\n' 'Run this setup with sudo.' >&2; exit 1; fi
. /etc/os-release
if [ "$ID" != ubuntu ]; then printf '%s\n' 'This setup supports Ubuntu. Review it for other operating systems.' >&2; exit 1; fi
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl git
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
printf '%s\n' "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $VERSION_CODENAME stable" > /etc/apt/sources.list.d/docker.list
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker
install -m 0700 -d /etc/nt118 /opt/nt118 /opt/nt118/backups
printf '%s\n' 'Docker is ready. Configure /etc/nt118/server.env before deploying.'
