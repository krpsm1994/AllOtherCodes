use strict;
print "\nSelect your development environment:\n";
print "a) Production (master)\n";
print "b) SAP.DAM.Hybris (main)\n";
print "c) SAP.DAMIntegration (main)\n";
print "d) SAP.DAMS4HANA (main)\n";
my $choice = <STDIN>;
my $p4env;
chomp $choice;
if( $choice eq 'a' ) { $p4env = 'C:\Drive\CodeBase\Git\Production\master'; }
elsif( $choice eq 'b' ) { $p4env = 'C:\Drive\CodeBase\Git\SAP.DAM.Hybris\main'; }
elsif( $choice eq 'c' ) { $p4env = 'C:\Drive\CodeBase\Git\SAP.DAMIntegration\main'; }
elsif( $choice eq 'd' ) { $p4env = 'C:\Drive\CodeBase\Git\SAP.DAMS4HANA\main'; }
else { die "Invalid choice\n"; }
print "\n";
open( FH, ">p4env_2.bat" ) or die "can't create p4env_2.bat";
print FH "\@cd $p4env\n";
print FH "\@p4env\n";
close( FH );
