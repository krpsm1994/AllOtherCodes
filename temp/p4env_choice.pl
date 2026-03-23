use strict;
print "\nSelect your development environment:\n";
print "a) Production (master)\n";
print "b) SAP.DAM.Hybris (main)\n";
my $choice = <STDIN>;
my $p4env;
chomp $choice;
if( $choice eq 'a' ) { $p4env = 'C:\Drive\CodeBase\temp\Production\master'; }
elsif( $choice eq 'b' ) { $p4env = 'C:\Drive\CodeBase\temp\SAP.DAM.Hybris\main'; }
else { die "Invalid choice\n"; }
print "\n";
open( FH, ">p4env_2.bat" ) or die "can't create p4env_2.bat";
print FH "\@cd $p4env\n";
print FH "\@p4env\n";
close( FH );
