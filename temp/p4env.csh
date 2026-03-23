echo
echo "Select your development environment:"
echo "a) Production (master)"
echo "b) SAP.DAM.Hybris (main)"
set choice=$<
if ( $choice == 'a' ) then
	set p4env='C:\Drive\CodeBase\temp\Production\master'
else if ( $choice == 'b' ) then
	set p4env='C:\Drive\CodeBase\temp\SAP.DAM.Hybris\main'
else
echo "Invalid choice"
return -1
endif

cd $p4env
source $p4env/p4env.csh
