import { describe, expect, it, vi, beforeEach } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import { defineComponent } from 'vue';
import NotificationSettingsPage from '../pages/NotificationSettingsPage.vue';
import { visibleNotificationTabs } from '../utils/notificationAccess';

const mock = vi.hoisted(() => ({ can: vi.fn() }));
vi.mock('../stores/auth', () => ({ useAuthStore: () => ({ can: mock.can }) }));
const admin = defineComponent({
  props: ['view'],
  template: '<div data-testid="admin">{{view}}</div>',
});

async function open(tab?: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/notification-policies',
        name: 'notification-policies',
        component: NotificationSettingsPage,
      },
    ],
  });
  await router.push({ name: 'notification-policies', query: tab ? { tab } : {} });
  await router.isReady();
  const wrapper = mount(defineComponent({ template: '<router-view />' }), {
    global: { plugins: [router], stubs: { NotificationAdmin: admin } },
  });
  await flushPromises();
  return { wrapper, router };
}

beforeEach(() => {
  mock.can.mockReturnValue(true);
});

describe('unified notification settings', () => {
  it('switches the three tabs within the same page and restores the selected tab', async () => {
    const { wrapper, router } = await open('channels');
    expect(wrapper.findAll('[role="tab"]')).toHaveLength(3);
    expect(wrapper.get('[data-testid="admin"]').text()).toBe('channels');
    await wrapper.findAll('[role="tab"]')[2]!.trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.name).toBe('notification-policies');
    expect(router.currentRoute.value.query.tab).toBe('deliveries');
    expect(wrapper.get('[data-testid="admin"]').text()).toBe('deliveries');
    wrapper.unmount();
  });

  it('shows only authorized tabs and never renders an unauthorized requested view', async () => {
    mock.can.mockImplementation((menu: string) => menu === 'notificationChannel');
    const { wrapper } = await open('policies');
    expect(wrapper.findAll('[role="tab"]')).toHaveLength(1);
    expect(wrapper.text()).toContain('通知渠道');
    expect(wrapper.get('[data-testid="admin"]').text()).toBe('channels');
    wrapper.unmount();
  });

  it('renders no business content when none of the three view permissions is granted', async () => {
    mock.can.mockReturnValue(false);
    expect(visibleNotificationTabs(mock.can)).toEqual([]);
    const { wrapper } = await open();
    expect(wrapper.find('[data-testid="admin"]').exists()).toBe(false);
    wrapper.unmount();
  });
});
