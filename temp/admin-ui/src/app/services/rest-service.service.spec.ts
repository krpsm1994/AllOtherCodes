import { TestBed } from '@angular/core/testing';

import { RestCallService } from './rest-call.service';

describe('RestCallService', () => {
  beforeEach(() => TestBed.configureTestingModule({}));

  it('should be created', () => {
    const service: RestCallService = TestBed.get(RestCallService);
    expect(service).toBeTruthy();
  });
});
