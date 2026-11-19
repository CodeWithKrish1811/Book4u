export const APP_NAME = 'Book4u';
export const APP_TAGLINE = 'Your Books. Your Campus. Your Community.';
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

export const SHARING_MODES = [
  {
    id: 'SWAP',
    title: 'Swap',
    tagline: 'Book for Book',
    description: 'Exchange a course textbook you no longer need for one required for your current semester.',
    badgeColor: 'badge-blue',
  },
  {
    id: 'GIVEAWAY',
    title: 'Giveaway',
    tagline: 'Zero-Cost Gifting',
    description: 'Pass down foundational textbooks to juniors without any financial or return obligation.',
    badgeColor: 'badge-emerald',
  },
  {
    id: 'LENDING',
    title: 'Lending',
    tagline: 'Time-Bound Borrowing',
    description: 'Temporarily lend reference books or exam guides for an agreed duration with a return date.',
    badgeColor: 'badge-amber',
  },
];
