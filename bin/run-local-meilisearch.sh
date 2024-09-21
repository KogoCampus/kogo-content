#!/bin/sh

sudo -v

echo "Running meilisearch container...\n"
if [[ ! $(which docker) && $(docker --version) ]]; then
    echo "Error! Docker is not available.\n"
    echo "check if 'docker ps' command is available."
    exit 1
fi

echo "Pulling Meilisearch image\n"
docker pull getmeili/meilisearch:latest

mount_path=$(pwd)
sudo mkdir -p mount_path
sudo chmod 755 mount_path

docker run -it --rm \
  -p 7700:7700 \
  -e MEILI_MASTER_KEY='MASTER_KEY'\
  -v $mount_path/tmp/meili_data:/meili_data \
  getmeili/meilisearch:v1.10
