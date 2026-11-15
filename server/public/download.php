<?php

http_response_code(410);
header('Content-Type: text/plain; charset=utf-8');
header('Cache-Control: no-store, max-age=0');
header('X-Content-Type-Options: nosniff');
exit('server_apk_download_disabled');
