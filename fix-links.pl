#!/usr/bin/perl

use strict;
use String::ShellQuote;

sub debug(@) {
    print STDERR "@_\n";
}

our $working_file_sq;
our $working_file;
our $working_file_dir;
sub fix_link($)
{
    my ($link) = @_;
    my $file;
    my $anchor;

    if ($link =~ m!http://baohaojun.github.(?:com|io)/(.*)!) {
        $file = $1;
    } elsif ($link =~ m/^file:/) {
        $link =~ s!^file:!!;
        $file = $link;
    } elsif ($link =~ m/:/) {
        return $link;
    } else {
        $file = $link;
    }

    $file =~ s!.*/!!;

    debug "file is $file";
    my $org_file = $file;
    if ($file =~ m/\.html$/) {
        $org_file =~ s/\.html$/.org/;
    }


    $org_file = shell_quote($org_file);
    chomp($org_file =
          (qx(find blog -name $org_file) or
           qx(find . -name $org_file)));

    if ($org_file =~ m/\n/) {
        die "$link matched with multiple files";
    } elsif (-e $org_file) {
        $file = $org_file;
    }

    if (-e $file) {
        debug "file is $file";
        $file = shell_quote($file);
        chomp ($file = qx(relative-path $file $working_file_sq));
        if ($file !~ m!/!) {
            $file = "./$file";
        }
        debug "file is $file";
        return $file;
    }

    return $link;
}

for my $filename (@ARGV) {
    open (my $fh, "<", $filename) or die "Can not open $filename";
    open (my $fh_new, ">", "$filename.$$") or die "Can not open $filename.$$";

    $working_file = $filename;
    ($working_file_dir = $filename) =~ s!(.*/).*!$1!;
    $working_file_sq = shell_quote($filename);
    my $changed = 0;
    while (<$fh>) {
        my $old = $_;
        s/\[\[(.*?)\]/"[[" . fix_link($1) . "]"/eg;
        print $fh_new $_;
        if ($_ ne $old) {
            $changed = 1;
        }
    }
    close $fh;
    close $fh_new;


    if ($changed) {
        rename "$filename.$$", $filename;
    } else {
        debug "remove $filename.$$";
        unlink "$filename.$$";
    }
}
