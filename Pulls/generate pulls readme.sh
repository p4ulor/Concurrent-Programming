# Create or overwrite the README.md file
echo "# Repository Links" > README.md
echo "" >> README.md

# Loop through all directories in the current directory
for dir in */; do
    # Check if the directory is a git repository
    if [ -d "$dir/.git" ]; then
        # Get the git origin URL
        origin_url=$(git -C "$dir" config --get remote.origin.url)
        
        # Add the repository name and origin URL to the README.md file
        echo "## ${dir%/}" >> README.md
        echo "$origin_url" >> README.md
        echo "" >> README.md
    fi
done

echo "README.md has been generated."