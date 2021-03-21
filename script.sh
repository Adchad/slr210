#!/bin/bash

i=1
while [ $i -le 5 ] 
do 
    mvn exec:exec | sed '10q;d' | grep -oP "(time :)\K(.*)"  >> output.txt 
    i=$(( $i + 1 ))
done