#!/bin/bash

projects=$( ls -d1 *solution | sed -e s/-solution// )

for x in $projects; do
	cd ${x}-solution
	
	mvn install -Dmaven.test.skip=true 2>&1 > /dev/null
	solbuild=$?
	mvn test 2>&1 > /dev/null
	soltest=$?

	cd ../${x}-assignment
	mvn install -Dmaven.test.skip=true 2>&1 > /dev/null
	assbuild=$?
	mvn test 2>&1 > /dev/null
	asstest=$?

	echo $solbuild $soltest $assbuild $asstest $x
	cd ..
done
