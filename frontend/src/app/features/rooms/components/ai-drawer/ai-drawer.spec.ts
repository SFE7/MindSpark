import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { SimpleChange } from '@angular/core';
import { of } from 'rxjs';
import { AiDrawerComponent } from './ai-drawer';
import { AiService } from '../../../../core/ai/ai.service';
import { AiRoomIdea } from '../../../../core/ai/ai.models';

describe('AiDrawerComponent', () => {
  let component: AiDrawerComponent;
  let fixture: ComponentFixture<AiDrawerComponent>;
  let aiService: AiService;

  const sampleIdeas: AiRoomIdea[] = [
    { id: '1', title: 'Idea 1', description: 'Desc 1', voteCount: 2 },
    { id: '2', title: 'Idea 2', description: 'Desc 2', voteCount: 5 },
    { id: '3', title: 'Idea 3', description: 'Desc 3', voteCount: 1 },
    { id: '4', title: 'Idea 4', description: 'Desc 4', voteCount: 0 },
    { id: '5', title: 'Idea 5', description: 'Desc 5', voteCount: 3 },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AiDrawerComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), AiService],
    }).compileComponents();

    fixture = TestBed.createComponent(AiDrawerComponent);
    component = fixture.componentInstance;
    aiService = TestBed.inject(AiService);

    component.isOpen = true;
    component.roomId = 'room-123';
    component.ideas = sampleIdeas;
    fixture.detectChanges();
  });

  it('creates successfully', () => {
    expect(component).toBeTruthy();
  });

  it('lazy loads usage and user tier when isOpen becomes true', () => {
    const loadSpy = vi.spyOn(aiService, 'loadUserAndUsage').mockReturnValue(
      of({
        user: { userId: 'u1', tier: 'FREE' },
        usage: { tier: 'FREE', dailyLimit: 10, usedToday: 2, remainingToday: 8, resetsAtUtc: '' },
      })
    );

    component.ngOnChanges({
      isOpen: new SimpleChange(false, true, true),
    });

    expect(loadSpy).toHaveBeenCalled();
  });

  describe('Compare 2–4 ideas selection validation rule', () => {
    beforeEach(() => {
      component['setMode']('room');
      component['setRoomTool']('compare');
      fixture.detectChanges();
    });

    it('toggles idea selections correctly', () => {
      expect(component['compareSelectedIds']().length).toBe(0);

      // Select 1 idea
      component['toggleCompareIdea']('1');
      expect(component['compareSelectedIds']()).toEqual(['1']);

      // Select 2nd idea
      component['toggleCompareIdea']('2');
      expect(component['compareSelectedIds']()).toEqual(['1', '2']);

      // Unselect 1st idea
      component['toggleCompareIdea']('1');
      expect(component['compareSelectedIds']()).toEqual(['2']);
    });

    it('enforces maximum 4 ideas selection cap', () => {
      component['toggleCompareIdea']('1');
      component['toggleCompareIdea']('2');
      component['toggleCompareIdea']('3');
      component['toggleCompareIdea']('4');
      expect(component['compareSelectedIds']().length).toBe(4);

      // Attempting to select 5th idea should be ignored
      component['toggleCompareIdea']('5');
      expect(component['compareSelectedIds']().length).toBe(4);
      expect(component['compareSelectedIds']()).toEqual(['1', '2', '3', '4']);
    });

    it('prevents running compare when fewer than 2 ideas are selected', () => {
      const compareSpy = vi.spyOn(aiService, 'compareIdeas');

      component['toggleCompareIdea']('1');
      component['runCompare']();

      expect(compareSpy).not.toHaveBeenCalled();
    });

    it('executes compare when 2 to 4 ideas are selected', () => {
      const compareSpy = vi.spyOn(aiService, 'compareIdeas').mockReturnValue(
        of({
          result: {
            criteria: ['Innovation'],
            comparison: [{ ideaId: '1', scores: {}, strengths: [], weaknesses: [] }],
            recommendation: { ideaId: '1', reason: 'Top pick' },
          },
          meta: { model: 'test', tier: 'FREE' },
        })
      );

      component['toggleCompareIdea']('1');
      component['toggleCompareIdea']('2');
      component['runCompare']();

      expect(compareSpy).toHaveBeenCalled();
    });
  });

  describe('Double-submit guard & loading protection', () => {
    it('does not dispatch generate request if aiService is already loading', () => {
      const genSpy = vi.spyOn(aiService, 'generateIdeas');
      aiService.loading.set(true);

      component['generateTopic'] = 'Test topic';
      component['runGenerate']();

      expect(genSpy).not.toHaveBeenCalled();
    });

    it('does not dispatch improve request if aiService is already loading', () => {
      const improveSpy = vi.spyOn(aiService, 'improveIdea');
      aiService.loading.set(true);

      component['selectedIdeaId'] = '1';
      component['runImprove']();

      expect(improveSpy).not.toHaveBeenCalled();
    });
  });

  describe('Canvas integration outputs', () => {
    it('emits addIdeaToCanvas when generated idea is added', () => {
      const addSpy = vi.spyOn(component.addIdeaToCanvas, 'emit');

      component['onAddGeneratedIdea']({
        title: 'New Idea',
        description: 'New Desc',
      });

      expect(addSpy).toHaveBeenCalledWith({
        title: 'New Idea',
        description: 'New Desc',
      });
      expect(component['addedItems']()['New Idea']).toBe(true);
    });

    it('emits updateIdeaOnCanvas when improved idea is accepted', () => {
      const updateSpy = vi.spyOn(component.updateIdeaOnCanvas, 'emit');

      component['selectedIdeaId'] = '1';
      component['improveResult'].set({
        result: { title: 'Polished Title', text: 'Polished Desc', changes: [] },
        meta: { model: 'test', tier: 'FREE' },
      });

      component['onUpdateCurrentIdea']();

      expect(updateSpy).toHaveBeenCalledWith({
        id: '1',
        title: 'Polished Title',
        description: 'Polished Desc',
      });
    });
  });
});
