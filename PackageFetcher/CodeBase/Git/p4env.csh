echo
echo "Select your development environment:"
echo "a) Production (master)"
echo "b) SAP.DAM.Hybris (main)"
echo "c) SAP.DAMIntegration (main)"
echo "d) SAP.DAMS4HANA (main)"
set choice=$<
if ( $choice == 'a' ) then
	set p4env='C:\Drive\CodeBase\Git\Production\master'
else if ( $choice == 'b' ) then
	set p4env='C:\Drive\CodeBase\Git\SAP.DAM.Hybris\main'
else if ( $choice == 'c' ) then
	set p4env='C:\Drive\CodeBase\Git\SAP.DAMIntegration\main'
else if ( $choice == 'd' ) then
	set p4env='C:\Drive\CodeBase\Git\SAP.DAMS4HANA\main'
else
echo "Invalid choice"
return -1
endif

cd $p4env
source $p4env/p4env.csh
