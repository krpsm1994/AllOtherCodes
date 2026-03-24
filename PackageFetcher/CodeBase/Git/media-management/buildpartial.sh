#svn up

#cd sdk
#ant clean-all package-all
#cd ..

cd server
./buildpackage.sh
cd ..

#cd ux
#./buildpackage.sh
#cd ..

cd restapi
./buildpackage.sh
cd ..

#cd ux-ws
#./buildpackage.sh
#cd ..

cd ux-html
./buildpackage.sh
cd ..

#cd webclient
#./buildpackage.sh
#cd ..
