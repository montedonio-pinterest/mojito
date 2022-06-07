#!/usr/bin/env bash

# This script will sync (push&pull) translations from Mojito for the woocommerce project.
# It must be run from the root of this repository.

echo "Install Mojito CLI"
source <(curl -L -N -s https://mojito.pinadmin.com/cli/install.sh)

echo "Push strings to Mojito"
mojito push -r woocommerce -s locale -ft PO

echo "Pull translations from Mojito"
mojito pull -r woocommerce -s locale -ft PO
