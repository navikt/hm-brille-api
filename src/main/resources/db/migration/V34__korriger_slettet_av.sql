UPDATE vedtak_slettet_v1
SET slettet_av = '[manuelt slettet]',
    slettet_av_type = 'NAV_ADMIN'
WHERE slettet < '2022-09-19T15:00:00'
;
