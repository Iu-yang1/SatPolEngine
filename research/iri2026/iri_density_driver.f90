! Official COSPAR/URSI IRI-2026 interface (compiled together with the official source).
! This file is SatPolEngine glue code; IRI source remains downloaded upstream.
! stdin records: year month day decimal_utc_hour geodetic_lat_deg lon_deg altitude_km
! stdout:      electron_density_m^-3 or explicit ERROR code, one line per record.
program iri_density_driver
  implicit none
  logical :: jf(50)
  integer :: year,month,day,mmdd,ios, ierr
  real :: uth, glat, glon, hkm
  real :: outf(20,1000), oarr(100)
  character(len=512) :: line

  do
     read(*,'(A)',iostat=ios) line
     if (ios/=0) exit
     if (len_trim(line)==0 .or. line(1:1)=='#') cycle
     read(line,*,iostat=ierr) year,month,day,uth,glat,glon,hkm
     if(ierr/=0) then
        write(*,'(A)') 'ERROR:INVALID_REQUEST'
        cycle
     endif
     if(year<1958.or.year>2030.or.month<1.or.month>12.or.day<1.or.day>31.or.&
        uth<0.or.uth>=24.or.abs(glat)>90.or.abs(glon)>360.or.hkm<80.or.hkm>2000) then
        write(*,'(A)') 'ERROR:OUT_OF_RANGE'
        cycle
     endif
     mmdd=month*100+day
     ! Set ALL switches explicitly to IRI 'on' rather than relying on
     ! undefined Fortran logical contents. Profile switches are validated
     ! during the end-to-end smoke test against physical density range.
     jf=.true.
     outf=0.0
     oarr=0.0
     ! IRI: jmag=0 geographical, UT indicated by dhour=25 + UTC hour.
     call iri_sub(jf,0,glat,glon,year,mmdd,uth+25.0,hkm,hkm,1.0,outf,oarr)
     if(outf(1,1) <= 0.0.or.outf(1,1)>1.0e14) then
       write(*,'(A)') 'ERROR:INVALID_DENSITY'
     else
       write(*,'(A,ES22.12)') 'SATPOL_NE=',outf(1,1)
     endif
  enddo
end program iri_density_driver
