#!/bin/bash

projects="$*"
if [ -z "$projects" ]; then
	projects=$( ls -d1 *solution | sed -e s/-solution// )
fi

for x in $projects; do
	for suffix in $(echo tests assignment solution); do
		echo ${x}-${suffix}

		cd ${x}-${suffix}
		
		repo=$(git remote get-url origin | sed -e 's/^.*github.com.//')
		gh repo create $repo --private --source=.
		if [ "$suffix" = "assignment" ]; then
			gh repo edit --template
		fi
		git push -u origin master
		
		cd ..
	done
done
