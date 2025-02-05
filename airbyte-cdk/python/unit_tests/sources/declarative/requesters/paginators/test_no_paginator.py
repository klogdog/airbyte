#
# Copyright (c) 2021 Airbyte, Inc., all rights reserved.
#

import requests
from airbyte_cdk.sources.declarative.requesters.paginators.no_pagination import NoPagination


def test():
    paginator = NoPagination()
    next_page_token = paginator.next_page_token(requests.Response(), [])
    assert next_page_token is None
