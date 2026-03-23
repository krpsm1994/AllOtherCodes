import { TestBed } from '@angular/core/testing';

import { OtmmSystemDetailsService } from './otmm-system-details.service';

describe('OtmmSystemDetailsService', () => {
  beforeEach(() => TestBed.configureTestingModule({}));

  it('should be created', () => {
    const service: OtmmSystemDetailsService = TestBed.get(OtmmSystemDetailsService);
    expect(service).toBeTruthy();
  });
});
