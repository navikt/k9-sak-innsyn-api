#!/bin/bash
# Exit if any command fails.
set -e

# --- Parse Input Arguments ---
# Initialize variables
CONTEXT=""
NAMESPACE=""
APP=""

# Process each parameter using flags
while [[ "$#" -gt 0 ]]; do
  case $1 in
    --context)
      CONTEXT="$2"
      shift 2
      ;;
    --namespace)
      NAMESPACE="$2"
      shift 2
      ;;
    --app)
      APP="$2"
      shift 2
      ;;
    *)
      echo "Unknown parameter passed: $1"
      echo "Usage: $0 --context <context> --namespace <namespace> --app <app>"
      exit 1
      ;;
  esac
done

# Check if all required parameters are provided
if [ -z "$CONTEXT" ] || [ -z "$NAMESPACE" ] || [ -z "$APP" ]; then
  echo "Missing required parameter."
  echo "Usage: $0 --context <context> --namespace <namespace> --app <app>"
  exit 1
fi

# --- Step 1: nais login ---
echo "Starting nais login. This command will open a browser for authentication..."
nais login
# The command waits until login is complete.

# --- Step 2: nais postgres prepare ---
echo -e "\nRunning 'nais postgres prepare' for:"
echo "  Context:   $CONTEXT"
echo "  Namespace: $NAMESPACE"
echo "  App:       $APP"
echo -e "\nPlease follow the instructions provided by the command below."
# Run prepare; it will prompt for confirmation (y/N) only once.
nais postgres prepare --context "$CONTEXT" --namespace "$NAMESPACE" "$APP"

# --- Step 3: nais postgres grant ---
echo -e "\nGranting PostgreSQL access with 'nais postgres grant'..."
nais postgres grant --context "$CONTEXT" --namespace "$NAMESPACE" "$APP"

# --- Step 4: nais postgres proxy ---
echo -e "\nStarting PostgreSQL proxy with 'nais postgres proxy'..."
nais postgres proxy --context "$CONTEXT" --namespace "$NAMESPACE" "$APP"

echo -e "\nAll commands executed successfully."
