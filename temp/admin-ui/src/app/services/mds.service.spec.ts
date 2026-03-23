import { TestBed } from '@angular/core/testing';

import { MdsService } from './mds.service';

describe('MdsService', () => {
  beforeEach(() => TestBed.configureTestingModule({}));

  it('should be created', () => {
    const service: MdsService = TestBed.get(MdsService);
    expect(service).toBeTruthy();
  });
});
